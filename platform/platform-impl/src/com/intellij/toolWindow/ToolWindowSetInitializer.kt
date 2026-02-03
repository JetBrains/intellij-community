// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.toolWindow

import com.intellij.diagnostic.PluginException
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.*
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.DesktopLayout
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.openapi.wm.impl.WindowInfoImpl
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.NonNls
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

@Suppress("SSBasedInspection")
private val LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.ToolWindowManagerImpl")

private inline fun Logger.debug(project: Project, lazyMessage: (project: String) -> @NonNls String) {
  if (isDebugEnabled) {
    // project.name must be not used - only projectFilePath is side-effect-free
    debug(lazyMessage(project.presentableUrl ?: ""))
  }
}

internal class ToolWindowSetInitializer(private val project: Project, private val manager: ToolWindowManagerImpl) {
  @Volatile
  private var isInitialized = false

  private val pendingLayout = AtomicReference<DesktopLayout>()
  private val pendingTasks = ConcurrentLinkedQueue<Runnable>()

  fun addToPendingTasksIfNotInitialized(task: Runnable): Boolean {
    if (isInitialized) {
      return false
    }
    else {
      pendingTasks.add(task)
      return true
    }
  }

  fun scheduleSetLayout(newLayout: DesktopLayout) {
    if (!isInitialized) {
      // will be executed once a project is loaded
      pendingLayout.set(newLayout)
      return
    }

    val app = ApplicationManager.getApplication()
    if (app.isDispatchThread) {
      pendingLayout.set(null)
      manager.setLayout(newLayout)
    }
    else {
      pendingLayout.set(newLayout)
      manager.coroutineScope.launch(Dispatchers.EDT) {
        manager.setLayout(pendingLayout.getAndSet(null) ?: return@launch)
      }
    }
  }

  suspend fun initUi(reopeningEditorJob: Job, taskListDeferred: Deferred<List<RegisterToolWindowTaskData>>?) {
    try {
      val tasks = taskListDeferred?.await()
      LOG.debug(project) { "create and layout tool windows (project=$it, tasks=${tasks?.joinToString(separator = "\n")}" }
      createAndLayoutToolWindows(manager = manager, tasks = tasks ?: return, reopeningEditorJob = reopeningEditorJob)
      // separate EDT task - ensure that more important tasks like editor restoring maybe executed
      span("toolwindow init pending tasks processing") {
        while (true) {
          val runnable = pendingTasks.poll() ?: break
          withContext(Dispatchers.EDT) {
            runnable.run()
          }
        }
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
    finally {
      LOG.debug(project) { "initialization completed (project=$it)" }
      isInitialized = true
    }
  }

  private suspend fun createAndLayoutToolWindows(
    manager: ToolWindowManagerImpl,
    tasks: List<RegisterToolWindowTaskData>,
    reopeningEditorJob: Job,
  ) {
    val ep = (ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl)
      .getExtensionPoint<RegisterToolWindowTaskProvider>("com.intellij.registerToolWindowTaskProvider")

    val layout = pendingLayout.getAndSet(null) ?: throw IllegalStateException("Expected some pending layout")
    val stripeManager = project.serviceAsync<ToolWindowStripeManager>()
    val list = span("toolwindow creating preparation") {
      addExtraTasks(tasks = tasks, project = project, ep = ep).map { task ->
        val existingInfo = layout.getInfo(task.id)
        val paneId = existingInfo?.safeToolWindowPaneId ?: WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
        PreparedRegisterToolWindowTask(
          task = task,
          existingInfo = existingInfo,
          paneId = paneId,
          isButtonNeeded = manager.isButtonNeeded(task = task, info = existingInfo, stripeManager = stripeManager),
        )
      }
    }

    val entries = withContext(Dispatchers.EDT) {
      @Suppress("TestOnlyProblems")
      manager.setLayoutOnInit(layout)

      span("toolwindow creating") {
        // Register all tool windows for the default tool window pane.
        // If there are any tool windows for other panes, we'll register them after the reopening editors job has created the panes.
        val entries = registerToolWindows(
          tasks = list,
          manager = manager,
          layout = layout,
          shouldRegister = { it == WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID },
        )
        for (toolWindowPane in manager.getToolWindowPanes()) {
          val buttonManager = toolWindowPane.buttonManager
          buttonManager.initMoreButton(project)
          buttonManager.updateResizeState(null)
          buttonManager.revalidateNotEmptyStripes()
          toolWindowPane.putClientProperty(UIUtil.NOT_IN_HIERARCHY_COMPONENTS, manager.createNotInHierarchyIterable(toolWindowPane.paneId))
        }

        entries
      }
    }

    serviceAsync<ToolWindowManagerImpl.ToolWindowManagerAppLevelHelper>()

    postEntryProcessing(entries)

    if (list.size != entries.size) {
      reopeningEditorJob.join()
      postEntryProcessing(withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        span("secondary frames toolwindow creation") {
          registerToolWindows(
            tasks = list,
            manager = manager,
            layout = manager.getLayout(),
            shouldRegister = { it != WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID },
          )
        }
      }, suffix = " (secondary)")
    }

    manager.registerEpListeners()
  }

  private suspend fun postEntryProcessing(entries: List<RegisterToolWindowResult>, suffix: String = "") {
    // dispatch event not in EDT
    span("toolWindowsRegistered event executing$suffix") {
      manager.project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowsRegistered(entries.map { it.entry.id }, manager)
    }

    span("ensureToolWindowActionRegistered executing$suffix") {
      val actionManager = serviceAsync<ActionManager>()
      for (result in entries) {
        ActivateToolWindowAction.Manager.ensureToolWindowActionRegistered(result.entry.toolWindow, actionManager)
      }
    }

    // ensure that the shortcuts of the actions registered above are included in tooltips
    span("stripeButton.updatePresentation executing$suffix", Dispatchers.UI) {
      for (result in entries) {
        result.entry.stripeButton?.updatePresentation()
      }
    }

    span("postTask executing$suffix") {
      for (result in entries) {
        if (result.postTask != null) {
          withContext(Dispatchers.EDT) {
            result.postTask.invoke()
          }
        }
      }
    }
  }
}

internal data class PreparedRegisterToolWindowTask(
  @JvmField val task: RegisterToolWindowTaskData,
  @JvmField val isButtonNeeded: Boolean,
  @JvmField val existingInfo: WindowInfoImpl?,

  @JvmField val paneId: String,
)

internal data class RegisterToolWindowResult(
  @JvmField val entry: ToolWindowEntry,
  @JvmField val postTask: (() -> Unit)?
)

private fun registerToolWindows(
  tasks: List<PreparedRegisterToolWindowTask>,
  manager: ToolWindowManagerImpl,
  layout: DesktopLayout,
  shouldRegister: (String) -> Boolean,
): List<RegisterToolWindowResult> {
  val entries = ArrayList<RegisterToolWindowResult>(tasks.size)
  for (task in tasks) {
    try {
      val paneId = task.paneId
      if (shouldRegister(paneId)) {
        // https://youtrack.jetbrains.com/issue/IDEA-335869/Tool-window-stripes-are-not-shown-for-detached-IDE-window-after-IDE-restart
        // we must compute button manager when pane is available
        entries.add(manager.registerToolWindow(
          preparedTask = task,
          buttonManager = manager.getToolWindowPane(task.paneId).buttonManager,
          layout = layout,
          ensureToolWindowActionRegistered = false,
        ))
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error(PluginException("Cannot init toolwindow ${task.task.contentFactory}", e, task.task.pluginDescriptor?.pluginId))
    }
  }
  return entries
}

private suspend fun addExtraTasks(
  tasks: List<RegisterToolWindowTaskData>,
  project: Project,
  ep: ExtensionPointImpl<RegisterToolWindowTaskProvider>,
): List<RegisterToolWindowTaskData> {
  if (ep.size() == 0) {
    return tasks
  }

  val result = tasks.toMutableList()
  // FacetDependentToolWindowManager - strictly speaking, computeExtraToolWindowBeans should be executed not in EDT, but for now it is not safe because:
  // 1. read action is required to read a facet list (might cause a deadlock)
  // 2. delay between a collection and adding ProjectWideFacetListener (should we introduce a new method in RegisterToolWindowTaskProvider to add listeners?)
  for (adapter in ep.sortedAdapters) {
    val pluginDescriptor = adapter.pluginDescriptor
    if (pluginDescriptor.pluginId != PluginManagerCore.CORE_ID) {
      LOG.error("Only bundled plugin can define registerToolWindowTaskProvider: $pluginDescriptor")
      continue
    }

    val provider = try {
      adapter.createInstance<RegisterToolWindowTaskProvider>(ApplicationManager.getApplication()) ?: continue
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error(e)
      continue
    }

    for (bean in provider.getTasks(project)) {
      beanToTask(project = project, bean = bean, plugin = bean.pluginDescriptor)?.let(result::add)
    }
  }
  return result
}

internal fun getToolWindowAnchor(factory: ToolWindowFactory?, bean: ToolWindowEP): ToolWindowAnchor {
  return factory?.anchor ?: ToolWindowAnchor.fromText(bean.anchor ?: ToolWindowAnchor.LEFT.toString())
}

private suspend fun beanToTask(project: Project, bean: ToolWindowEP, plugin: PluginDescriptor): RegisterToolWindowTaskData? {
  val factory = bean.getToolWindowFactory(plugin)
  return if (factory.isApplicableAsync(project)) beanToTask(project = project, bean = bean, plugin = plugin, factory = factory) else null
}

private fun beanToTask(
  project: Project,
  bean: ToolWindowEP,
  plugin: PluginDescriptor,
  factory: ToolWindowFactory,
): RegisterToolWindowTaskData {
  return RegisterToolWindowTaskData(
    id = bean.id,
    icon = findIconFromBean(bean = bean, factory = factory, pluginDescriptor = plugin),
    anchor = getToolWindowAnchor(factory, bean),
    sideTool = bean.secondary || (@Suppress("DEPRECATION") bean.side),
    canCloseContent = bean.canCloseContents,
    canWorkInDumbMode = DumbService.isDumbAware(factory),
    shouldBeAvailable = factory.shouldBeAvailable(project),
    contentFactory = factory,
    stripeTitle = getStripeTitleSupplier(id = bean.id, project = project, pluginDescriptor = plugin),
    pluginDescriptor = plugin,
  )
}

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun computeToolWindowBeans(project: Project): List<RegisterToolWindowTaskData> {
  return coroutineScope {
    ToolWindowEP.EP_NAME.filterableLazySequence().map { item ->
      async {
        try {
          val bean = item.instance ?: return@async null
          val condition = bean.getCondition(item.pluginDescriptor)
          if (condition == null || condition.value(project)) {
            beanToTask(project = project, bean = bean, plugin = item.pluginDescriptor)
          }
          else {
            null
          }
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Throwable) {
          LOG.error("Cannot process toolwindow ${item.id}", e)
          null
        }
      }
    }
      .toList()
  }.mapNotNull { it.getCompleted() }
}