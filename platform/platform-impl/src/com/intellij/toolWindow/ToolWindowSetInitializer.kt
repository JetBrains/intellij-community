// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.toolWindow

import com.intellij.diagnostic.PluginException
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.DesktopLayout
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
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
    // project.name must be not used - only projectFilePath is side effect free
    debug(lazyMessage(project.presentableUrl ?: ""))
  }
}

// open for rider
class ToolWindowSetInitializer(private val project: Project, private val manager: ToolWindowManagerImpl) {
  @Volatile
  private var isInitialized = false

  private val pendingLayout = AtomicReference<DesktopLayout?>()
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
      @Suppress("DEPRECATION")
      project.coroutineScope.launch(Dispatchers.EDT) {
        manager.setLayout(pendingLayout.getAndSet(null) ?: return@launch)
      }
    }
  }

  suspend fun initUi(reopeningEditorJob: Job, taskListDeferred: Deferred<List<RegisterToolWindowTask>>?) {
    try {
      val tasks = taskListDeferred?.await()
      LOG.debug(project) { "create and layout tool windows (project=$it, tasks=${tasks?.joinToString(separator = "\n")}" }
      createAndLayoutToolWindows(manager = manager, tasks = tasks ?: return, reopeningEditorJob = reopeningEditorJob)
      // separate EDT task - ensure that more important tasks like editor restoring maybe executed
      span("toolwindow init pending tasks processing", Dispatchers.EDT) {
        blockingContext {
          while (true) {
            (pendingTasks.poll() ?: break).run()
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

  private suspend fun createAndLayoutToolWindows(manager: ToolWindowManagerImpl,
                                                 tasks: List<RegisterToolWindowTask>,
                                                 reopeningEditorJob: Job) {
    val ep = (ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl)
      .getExtensionPoint<RegisterToolWindowTaskProvider>("com.intellij.registerToolWindowTaskProvider")
    val list = addExtraTasks(tasks, project, ep)

    val entries = withContext(Dispatchers.EDT) {
      val layout = pendingLayout.getAndSet(null) ?: throw IllegalStateException("Expected some pending layout")
      @Suppress("TestOnlyProblems")
      manager.setLayoutOnInit(layout)

      span("toolwindow creating") {
        // Register all tool windows for the default tool window pane.
        // If there are any tool windows for other panes, we'll register them after the reopening editors job has created the panes.
        val entries = registerToolWindows(list, manager, layout) { it == WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID }
        for (toolWindowPane in manager.getToolWindowPanes()) {
          toolWindowPane.buttonManager.initMoreButton()
          toolWindowPane.buttonManager.revalidateNotEmptyStripes()
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
          registerToolWindows(registerTasks = list,
                              manager = manager,
                              layout = manager.getLayout(),
                              shouldRegister = { it != WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID })
        }
      }, suffix = " (secondary)")
    }

    manager.registerEpListeners()
  }

  private suspend fun postEntryProcessing(entries: List<ToolWindowEntry>, suffix: String = "") {
    // dispatch event not in EDT
    span("toolWindowsRegistered event executing$suffix") {
      manager.project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowsRegistered(entries.map { it.id }, manager)
    }

    span("ensureToolWindowActionRegistered executing$suffix") {
      val actionManager = ApplicationManager.getApplication().serviceAsync<ActionManager>()
      for (entry in entries) {
        ActivateToolWindowAction.ensureToolWindowActionRegistered(entry.toolWindow, actionManager)
      }
    }
  }
}

private fun registerToolWindows(registerTasks: List<RegisterToolWindowTask>,
                                manager: ToolWindowManagerImpl,
                                layout: DesktopLayout,
                                shouldRegister: (String) -> Boolean): List<ToolWindowEntry> {
  val entries = ArrayList<ToolWindowEntry>(registerTasks.size)
  for (task in registerTasks) {
    try {
      val paneId = layout.getInfo(task.id)?.safeToolWindowPaneId ?: WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
      if (shouldRegister(paneId)) {
        entries.add(manager.registerToolWindow(task = task, buttonManager = manager.getToolWindowPane(paneId).buttonManager))
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error(PluginException("Cannot init toolwindow ${task.contentFactory}", e, task.pluginDescriptor?.pluginId))
    }
  }
  return entries
}

private suspend fun addExtraTasks(tasks: List<RegisterToolWindowTask>,
                                  project: Project,
                                  ep: ExtensionPointImpl<RegisterToolWindowTaskProvider>): List<RegisterToolWindowTask> {
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

    for (bean in withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) { provider.getTasks(project) }) {
      beanToTask(project = project, bean = bean, plugin = bean.pluginDescriptor)?.let(result::add)
    }
  }
  return result
}

internal fun getToolWindowAnchor(factory: ToolWindowFactory?, bean: ToolWindowEP): ToolWindowAnchor {
  return factory?.anchor ?: ToolWindowAnchor.fromText(bean.anchor ?: ToolWindowAnchor.LEFT.toString())
}

private suspend fun beanToTask(project: Project, bean: ToolWindowEP, plugin: PluginDescriptor): RegisterToolWindowTask? {
  val factory = bean.getToolWindowFactory(plugin)
  return if (factory.isApplicableAsync(project)) beanToTask(project = project, bean = bean, plugin = plugin, factory = factory) else null
}

private fun beanToTask(project: Project,
                       bean: ToolWindowEP,
                       plugin: PluginDescriptor,
                       factory: ToolWindowFactory): RegisterToolWindowTask {
  val task = RegisterToolWindowTask(
    id = bean.id,
    icon = findIconFromBean(bean, factory, plugin),
    anchor = getToolWindowAnchor(factory, bean),
    sideTool = bean.secondary || (@Suppress("DEPRECATION") bean.side),
    canCloseContent = bean.canCloseContents,
    canWorkInDumbMode = DumbService.isDumbAware(factory),
    shouldBeAvailable = factory.shouldBeAvailable(project),
    contentFactory = factory,
    stripeTitle = getStripeTitleSupplier(id = bean.id, project = project, pluginDescriptor = plugin),
  )
  task.pluginDescriptor = plugin
  return task
}

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun computeToolWindowBeans(project: Project): List<RegisterToolWindowTask> {
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