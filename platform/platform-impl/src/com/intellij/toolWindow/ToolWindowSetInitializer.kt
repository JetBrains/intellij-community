// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.runActivity
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.DesktopLayout
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.ui.ExperimentalUI
import com.intellij.util.containers.addIfNotNull
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.CancellationException
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

  private val initFuture: Deferred<Ref<List<RegisterToolWindowTask>?>>
  private val pendingLayout = AtomicReference<DesktopLayout?>()

  private val pendingTasks = ConcurrentLinkedQueue<Runnable>()

  init {
    val app = ApplicationManager.getApplication()
    if (project.isDefault || app.isUnitTestMode || app.isHeadlessEnvironment) {
      initFuture = CompletableDeferred(value = Ref())
    }
    else {
      initFuture = project.coroutineScope.async {
        Ref(runActivity("toolwindow init command creation") {
          computeToolWindowBeans(project)
        })
      }
    }
  }

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
      // will be executed once project is loaded
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
      app.invokeLater({
                        manager.setLayout(pendingLayout.getAndSet(null) ?: return@invokeLater)
                      }, project.disposed)
    }
  }

  suspend fun initUi(reopeningEditorsJob: Job) {
    try {
      val ref = initFuture.await()
      val tasks = ref.get()
      LOG.debug(project) { "create and layout tool windows (project=$it, tasks=${tasks?.joinToString(separator = "\n")}" }
      ref.set(null)
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        createAndLayoutToolWindows(manager, tasks ?: return@withContext, reopeningEditorsJob)
        while (true) {
          (pendingTasks.poll() ?: break).run()
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

  // must be executed in EDT
  private suspend fun createAndLayoutToolWindows(manager: ToolWindowManagerImpl, tasks: List<RegisterToolWindowTask>, reopeningEditorsJob: Job) {

    fun registerToolWindows(registerTasks: List<RegisterToolWindowTask>, shouldRegister: (String) -> Boolean): Boolean {
      val entries = ArrayList<String>(registerTasks.size)
      registerTasks.forEach { task ->
        try {
          manager.getLayout().getInfo(task.id)?.safeToolWindowPaneId?.let { paneId ->
            if (shouldRegister(paneId)) {
              val toolWindowPane = manager.getToolWindowPane(paneId)
              entries.add(manager.registerToolWindow(task, toolWindowPane.buttonManager).id)
            }
          }
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (e: Throwable) {
          LOG.error(PluginException("Cannot init toolwindow ${task.contentFactory}", e, task.pluginDescriptor?.pluginId))
        }
      }

      project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowsRegistered(entries, manager)

      return entries.size == registerTasks.size
    }

    @Suppress("TestOnlyProblems")
    manager.setLayoutOnInit(pendingLayout.getAndSet(null) ?: throw IllegalStateException("Expected some pending layout"))

    // FacetDependentToolWindowManager - strictly speaking, computeExtraToolWindowBeans should be executed not in EDT, but for now it is not safe because:
    // 1. read action is required to read facet list (might cause a deadlock)
    // 2. delay between collection and adding ProjectWideFacetListener (should we introduce a new method in RegisterToolWindowTaskProvider to add listeners?)
    val list = addExtraTasks(tasks, project)
    val hasSecondaryFrameToolWindows = runActivity("toolwindow creating") {
      // Register all tool windows for the default tool window pane. If there are any tool windows for other panes, we'll register them
      // after the reopening editors job has created the panes
      val registeredAll = registerToolWindows(list) { it == WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID }

      manager.getToolWindowPanes().forEach {
        it.buttonManager.initMoreButton()
        it.buttonManager.revalidateNotEmptyStripes()
        it.putClientProperty(UIUtil.NOT_IN_HIERARCHY_COMPONENTS, manager.createNotInHierarchyIterable(it.paneId))
      }

      return@runActivity !registeredAll
    }
    service<ToolWindowManagerImpl.ToolWindowManagerAppLevelHelper>()

    registerEpListeners(manager)

    if (hasSecondaryFrameToolWindows) {
      reopeningEditorsJob.join()
      runActivity("secondary frames toolwindow creation") {
        registerToolWindows(list) { it != WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID }
      }
    }
  }
}

private fun addExtraTasks(tasks: List<RegisterToolWindowTask>, project: Project): List<RegisterToolWindowTask> {
  val ep = (ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl)
    .getExtensionPoint<RegisterToolWindowTaskProvider>("com.intellij.registerToolWindowTaskProvider")
  if (ep.size() <= 0) {
    return tasks
  }
  val result = tasks.toMutableList()
  ep.processImplementations(true) { supplier, epPluginDescriptor ->
    if (epPluginDescriptor.pluginId == PluginManagerCore.CORE_ID) {
      for (bean in (supplier.get() ?: return@processImplementations).getTasks(project)) {
        result.addIfNotNull(beanToTask(project, bean))
      }
    }
    else {
      LOG.error("Only bundled plugin can define registerToolWindowTaskProvider: $epPluginDescriptor")
    }
  }
  return result
}

// This method cannot be inlined because of magic Kotlin compilation bug: it 'captured' "list" local value and cause class-loader leak
// See IDEA-CR-61904
private fun registerEpListeners(manager: ToolWindowManagerImpl) {
  ToolWindowEP.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<ToolWindowEP> {
    override fun extensionAdded(extension: ToolWindowEP, pluginDescriptor: PluginDescriptor) {
      manager.initToolWindow(extension, pluginDescriptor)
    }

    override fun extensionRemoved(extension: ToolWindowEP, pluginDescriptor: PluginDescriptor) {
      manager.doUnregisterToolWindow(extension.id)
    }
  }, manager.project)
}

internal fun getToolWindowAnchor(factory: ToolWindowFactory?, bean: ToolWindowEP): ToolWindowAnchor {
  return factory?.anchor ?: ToolWindowAnchor.fromText(bean.anchor ?: ToolWindowAnchor.LEFT.toString())
}

private fun beanToTask(project: Project, bean: ToolWindowEP, plugin: PluginDescriptor = bean.pluginDescriptor): RegisterToolWindowTask? {
  val factory = bean.getToolWindowFactory(plugin)
  return if (factory.isApplicable(project)) beanToTask(project, bean, plugin, factory) else null
}

private fun beanToTask(project: Project,
                       bean: ToolWindowEP,
                       plugin: PluginDescriptor,
                       factory: ToolWindowFactory): RegisterToolWindowTask {
  val task = RegisterToolWindowTask(
    id = bean.id,
    icon = findIconFromBean(bean, factory, plugin),
    anchor = getToolWindowAnchor(factory, bean),
    sideTool = (bean.secondary || (@Suppress("DEPRECATION") bean.side)) && !ExperimentalUI.isNewUI(),
    canCloseContent = bean.canCloseContents,
    canWorkInDumbMode = DumbService.isDumbAware(factory),
    shouldBeAvailable = factory.shouldBeAvailable(project),
    contentFactory = factory,
    stripeTitle = getStripeTitleSupplier(bean.id, project, plugin),
  )
  task.pluginDescriptor = bean.pluginDescriptor
  return task
}

@VisibleForTesting
internal fun computeToolWindowBeans(project: Project): List<RegisterToolWindowTask> {
  val ep = ToolWindowEP.EP_NAME.point as ExtensionPointImpl<ToolWindowEP>
  val list = ArrayList<RegisterToolWindowTask>(ep.size())
  ep.processWithPluginDescriptor(true) { bean, pluginDescriptor ->
    try {
      val condition = bean.getCondition(pluginDescriptor)
      if (condition == null || condition.value(project)) {
        list.addIfNotNull(beanToTask(project, bean, pluginDescriptor))
      }
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error("Cannot process toolwindow ${bean.id}", e)
    }
  }
  return list
}