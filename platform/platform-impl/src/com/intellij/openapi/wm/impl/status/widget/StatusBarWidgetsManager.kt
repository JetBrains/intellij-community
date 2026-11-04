// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.openapi.wm.impl.status.widget

import com.intellij.diagnostic.PluginException
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.TechnologyAwareExtensionPointName
import com.intellij.openapi.extensions.TechnologyFilter
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.StatusBarWidgetProvider
import com.intellij.openapi.wm.WidgetPresentationDataContext
import com.intellij.openapi.wm.WidgetPresentationFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.ex.WindowManagerListener
import com.intellij.openapi.wm.impl.IdeFrameGuests
import com.intellij.openapi.wm.impl.status.ChildStatusBarWidget
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.openapi.wm.impl.status.createComponentByWidgetPresentation
import com.intellij.platform.util.coroutines.attachAsChildTo
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.asSafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import kotlin.coroutines.CoroutineContext

private val LOG = logger<StatusBarWidgetsManager>()

@Service(Service.Level.PROJECT)
class StatusBarWidgetsManager(
  private val project: Project,
  private val parentScope: CoroutineScope,
) : SimpleModificationTracker(), Disposable {
  private var wasInitialized = false
  private val widgetFactories = LinkedHashMap<StatusBarWidgetFactory, StatusBarWidget>()
  private val widgetIdMap = HashMap<String, StatusBarWidgetFactory>()

  init {
    StatusBarActionManager.getInstance()
  }

  /**
   * The data context of the widgets of the frame of [project]. The widgets keep [project] as their project, but the current
   * editor is the selected editor of the active project of the frame, see `WindowManagerEx.getActiveProject`: a frame with
   * guests shows the editors of one project at a time.
   */
  @get:ApiStatus.Internal
  val dataContext: WidgetPresentationDataContext = object : WidgetPresentationDataContext {
    override val project: Project
      get() = this@StatusBarWidgetsManager.project

    @OptIn(ExperimentalCoroutinesApi::class)
    override val currentFileEditor: StateFlow<FileEditor?> by lazy {
      frameActiveProjectFlow(project)
        .flatMapLatest { active ->
          flow {
            // a guest that leaves gives the frame back to the owner, and its manager is gone once the guest is disposed
            if (active !== project && active.isDisposed) {
              emit(null)
            }
            else {
              emitAll(active.serviceAsync<FileEditorManager>().selectedEditorFlow)
            }
          }
        }
        .stateIn(parentScope, started = SharingStarted.Eagerly, initialValue = null)
    }
  }

  fun updateAllWidgets() {
    synchronized(widgetFactories) {
      for (factory in widgetFactories.keys.toList()) {
        updateWidget(factory)
      }
    }
  }

  fun updateWidget(factoryExtension: Class<out StatusBarWidgetFactory>) {
    val factory = StatusBarWidgetFactory.EP_NAME.findExtension(factoryExtension)
    if (factory == null) {
      LOG.warn("Factory is not registered as `com.intellij.statusBarWidgetFactory` extension: ${factoryExtension.name} [1]")
    }
    else {
      updateWidget(factory)
    }
  }

  @JvmOverloads
  fun updateWidget(factory: StatusBarWidgetFactory, coroutineContext: CoroutineContext = Dispatchers.EDT) {
    // the declaration of the factory gives the module that decides the applicability, and the order of the widget
    val extension = StatusBarWidgetFactory.EP_NAME.filterableLazySequence().firstOrNull { it.id == factory.id }
    if (extension == null) {
      updateUnregisteredWidget(factory)
      return
    }

    if (!TechnologyFilter.getInstance().isApplicable(extension.pluginDescriptor, project) || !isEnabled(factory)) {
      disableWidget(factory)
      return
    }

    synchronized(widgetFactories) {
      if (!wasInitialized) {
        // do not initialize widgets too early by eager listeners
        return
      }

      if (widgetFactories.containsKey(factory)) {
        // this widget is already enabled
        return
      }

      val order = extension.order
      val widget = createWidget(factory, dataContext, parentScope)
      widgetFactories.put(factory, widget)
      widgetIdMap.put(widget.ID(), factory)
      parentScope.launch(coroutineContext) {
        when (val statusBar = WindowManager.getInstance().getStatusBar(project)) {
          is IdeStatusBarImpl -> statusBar.addWidget(widget, order)
          null -> {
            PluginException.logPluginError(LOG, "Cannot add a widget for project without root status bar: ${factory.id}", null, factory.javaClass)
          }
          else -> {
            @Suppress("DEPRECATION")
            statusBar.addWidget(widget, order.toString())
          }
        }
      }
    }
  }

  /**
   * Updates the widget of a factory that is not an extension of `com.intellij.statusBarWidgetFactory`: the adapter of a deprecated
   * `StatusBarWidgetProvider` that [init] created, or a factory nobody registered. Such a factory has no declaration to take the module
   * and the loading order from: its widget is disabled when the factory stopped being available, and it cannot be added here.
   */
  private fun updateUnregisteredWidget(factory: StatusBarWidgetFactory) {
    if (!isEnabled(factory)) {
      disableWidget(factory)
      return
    }

    synchronized(widgetFactories) {
      if (wasInitialized && !widgetFactories.containsKey(factory)) {
        LOG.warn("Factory is not registered as `com.intellij.statusBarWidgetFactory` extension: ${factory.id} [2]")
      }
    }
  }

  /** Whether the settings, the availability in the project and the mode of the application allow the widget of the factory. */
  private fun isEnabled(factory: StatusBarWidgetFactory): Boolean {
    return (!factory.isConfigurable || StatusBarWidgetSettings.getInstance().isEnabled(factory)) &&
           factory.isAvailable(project) &&
           factory.isAllowedByInternalMode()
  }

  /** The factory whose widget exists, by the id of its declaration; `null` when the widget does not exist. */
  private fun findExtensionWidgetFactory(factoryId: String?): StatusBarWidgetFactory? {
    synchronized(widgetFactories) {
      return widgetFactories.keys.firstOrNull { it.id == factoryId }
    }
  }

  fun wasWidgetCreated(factoryId: String): Boolean {
    return synchronized(widgetFactories) {
      widgetFactories.keys.any { it.id.equals(factoryId, ignoreCase = true) }
    }
  }

  override fun dispose() {
    parentScope.cancel()
  }

  fun findWidgetFactory(widgetId: String): StatusBarWidgetFactory? = widgetIdMap.get(widgetId)

  fun getWidgetFactories(): Set<StatusBarWidgetFactory> {
    val isLightEditProject = LightEdit.owns(project)
    val result = LinkedHashSet<StatusBarWidgetFactory>()
    TECH_AWARE_FACTORIES.lazySequence(project).filterTo(result) {
      (!isLightEditProject || it is LightEditCompatible) && it.isAllowedByInternalMode()
    }
    synchronized(widgetFactories) {
      @Suppress("removal", "DEPRECATION")
      StatusBarWidgetProvider.EP_NAME.extensionList.mapNotNullTo(result) { provider ->
        widgetFactories.keys.firstOrNull { it is StatusBarWidgetProviderToFactoryAdapter && it.provider === provider }
      }
    }
    return result
  }

  private fun disableWidget(factory: StatusBarWidgetFactory) {
    synchronized(widgetFactories) {
      val createdWidget = widgetFactories.remove(factory) ?: return
      val key = createdWidget.ID()
      widgetIdMap.remove(key)
      factory.disposeWidget(createdWidget)
      WindowManager.getInstance().getStatusBar(project)?.removeWidget(key)
    }
  }

  fun canBeEnabledOnStatusBar(factory: StatusBarWidgetFactory, statusBar: StatusBar): Boolean {
    return factory.isAvailable(project) &&
           factory.isAllowedByInternalMode() &&
           factory.isConfigurable &&
           factory.canBeEnabledOn(statusBar)
  }

  internal fun init(frame: IdeFrame): List<Pair<StatusBarWidget, LoadingOrder>> {
    val isLightEditProject = LightEdit.owns(project)
    val statusBarWidgetSettings = StatusBarWidgetSettings.getInstance()
    val availableFactories: List<Pair<StatusBarWidgetFactory, LoadingOrder>> = TECH_AWARE_FACTORIES.filterableLazySequence(project)
      .filter {
        val id = it.id
        if (id == null) {
          LOG.warn(
            "${it.implementationClassName} doesn't define 'id' for extension (point=com.intellij.statusBarWidgetFactory). " +
            "Please specify `id` attribute. Plugin ID: ${it.pluginDescriptor.pluginId}"
          )
          true
        }
        else {
          !statusBarWidgetSettings.isExplicitlyDisabled(id)
        }
      }
      .mapNotNull { (it.instance ?: return@mapNotNull null) to it.order }
      .filter { !isLightEditProject || it.first is LightEditCompatible }
      .toList()

    val pendingFactories = availableFactories.toMutableList()

    @Suppress("removal", "DEPRECATION")
    StatusBarWidgetProvider.EP_NAME.extensionList.mapTo(pendingFactories) {
      StatusBarWidgetProviderToFactoryAdapter(project, frame, it) to LoadingOrder.anchorToOrder(it.anchor)
    }

    pendingFactories.removeAll { (factory, _) -> !isEnabled(factory) }

    val widgets = synchronized(widgetFactories) {
      wasInitialized = true
      
      val result = mutableListOf<Pair<StatusBarWidget, LoadingOrder>>()

      for ((factory, anchor) in pendingFactories) {
        if (widgetFactories.containsKey(factory)) {
          PluginException.logPluginError(LOG, "Factory has been added already: ${factory.id}", null, factory.javaClass)
          continue
        }

        val widget = createWidget(factory, dataContext, parentScope)
        widgetFactories[factory] = widget
        widgetIdMap[widget.ID()] = factory
        result.add(widget to anchor)
      }

      result
    }

    incModificationCount()

    StatusBarWidgetFactory.EP_NAME.addExtensionPointListener(parentScope, object : ExtensionPointListener<StatusBarWidgetFactory> {
      // a removed factory loses its widget right away, while its plugin is still loaded; the recompute below has no instance for that
      override fun extensionRemoved(extension: StatusBarWidgetFactory, pluginDescriptor: PluginDescriptor) {
        synchronized(widgetFactories) {
          disableWidget(extension)
          widgetFactories.remove(extension)
          incModificationCount()
        }
      }
    })

    // the widgets follow the techs of the project and the loaded plugins alike: the widget of a factory that stopped being applicable
    // is disabled and the one of a factory that became applicable (its tech got enabled, or its plugin got loaded) is enabled, by the
    // per-factory update that also decides on the settings and the availability. A factory of a disabled tech is not created for that:
    // only the one whose widget exists is touched
    TECH_AWARE_FACTORIES.addChangeListener(parentScope) { _ ->
      ApplicationManager.getApplication().invokeLater(
        {
          val isLightEditProject = LightEdit.owns(project)
          for (item in StatusBarWidgetFactory.EP_NAME.filterableLazySequence()) {
            if (TechnologyFilter.getInstance().isApplicable(item.pluginDescriptor, project)) {
              val factory = item.instance ?: continue
              if (!isLightEditProject || factory is LightEditCompatible) {
                updateWidget(factory)
              }
            }
            else {
              findExtensionWidgetFactory(item.id)?.let { disableWidget(it) }
            }
          }
          incModificationCount()
        }, project.disposed)
    }

    return widgets
  }
}

/** The widget factories applicable to a project: the ones whose techs the project has enabled. */
private val TECH_AWARE_FACTORIES = TechnologyAwareExtensionPointName(StatusBarWidgetFactory.EP_NAME)

/**
 * Tells if the current mode of the application allows the widget of the factory.
 * An internal widget needs the internal mode.
 */
internal fun StatusBarWidgetFactory.isAllowedByInternalMode(): Boolean {
  return !isInternal || ApplicationManager.getApplication().isInternal
}

/**
 * The active project of the frame of [project], see `WindowManagerEx.getActiveProject`, and each change of it.
 * A frame guest has no frame of its own, so it gives itself.
 *
 * The record is read as is, not through [IdeFrameGuests.activeProjectOf]: a guest that turns active while it still opens
 * gets no second event once it is initialized, and the status bar would show the owner for good.
 */
private fun frameActiveProjectFlow(project: Project): Flow<Project> {
  fun activeProject(): Project {
    return if (IdeFrameGuests.isFrameGuest(project)) project else WindowManagerEx.getInstanceEx().getActiveProject(project)
  }

  // the callback only signals, and the collector reads the record: a stale read can't overwrite a newer one
  return callbackFlow {
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(WindowManagerListener.TOPIC, object : WindowManagerListener {
      override fun onFramesChanged() {
      }

      override fun onActiveProjectChanged(frameOwner: Project, activeProject: Project) {
        if (frameOwner === project) {
          trySend(Unit)
        }
      }
    })
    // signal after the subscription, so that no change is lost
    trySend(Unit)
    awaitClose()
  }
    .conflate()
    .map { activeProject() }
    .distinctUntilChanged { old, new -> old === new }
}

private fun createWidget(
  factory: StatusBarWidgetFactory,
  dataContext: WidgetPresentationDataContext,
  parentScope: CoroutineScope,
): StatusBarWidget {
  if (factory !is WidgetPresentationFactory) {
    return factory.createWidget(dataContext.project, parentScope)
  }

  val widgetScope = parentScope.childScope("${factory.id}-widget")
  tryAttachWidgetScopeToPlugin(factory, widgetScope)

  return WidgetPresentationWrapper(id = factory.id, factory = factory, dataContext = dataContext, scope = widgetScope)
}

private fun tryAttachWidgetScopeToPlugin(factory: StatusBarWidgetFactory, widgetScope: CoroutineScope) {
  val pluginScope = factory.javaClass.classLoader.asSafely<PluginAwareClassLoader>()?.pluginCoroutineScope
  if (pluginScope != null) {
    widgetScope.attachAsChildTo(pluginScope)
  }
}

/**
 * Wrapper for V2 status bar widgets (those using WidgetPresentationFactory).
 * Implements ChildStatusBarWidget to support child status bars in detached windows.
 */
internal class WidgetPresentationWrapper(
  private val id: String,
  private val factory: WidgetPresentationFactory,
  private val dataContext: WidgetPresentationDataContext,
  private val scope: CoroutineScope,
) : StatusBarWidget, CustomStatusBarWidget, ChildStatusBarWidget {
  internal val factoryClass: Class<out WidgetPresentationFactory>
    get() = factory.javaClass

  override fun ID(): String = id

  override fun install(statusBar: StatusBar) {}

  override fun getComponent(): JComponent {
    return createComponentByWidgetPresentation(
      factory.createPresentation(dataContext, scope),
      dataContext.project,
      scope
    )
  }

  override fun dispose() {
    scope.cancel()
  }

  override fun createForChild(childStatusBar: IdeStatusBarImpl): StatusBarWidget {
    val childScope = childStatusBar.coroutineScope.childScope("$id-widget")
    return WidgetPresentationWrapper(
      id = id,
      factory = factory,
      dataContext = object : WidgetPresentationDataContext {
        override val project: Project
          get() = requireNotNull(childStatusBar.project) { "Project is null for child status bar, probably already disposed" }

        override val currentFileEditor: StateFlow<FileEditor?>
          get() = childStatusBar.currentEditor
      },
      scope = childScope,
    )
  }
}
