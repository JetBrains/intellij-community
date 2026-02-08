// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.openapi.wm.impl.status.widget

import com.intellij.diagnostic.PluginException
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.PluginDescriptor
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
import com.intellij.openapi.wm.impl.status.ChildStatusBarWidget
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.openapi.wm.impl.status.createComponentByWidgetPresentation
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

  internal val dataContext: WidgetPresentationDataContext = object : WidgetPresentationDataContext {
    override val project: Project
      get() = this@StatusBarWidgetsManager.project

    override val currentFileEditor: StateFlow<FileEditor?> by lazy {
      flow {
        val manager = project.serviceAsync<FileEditorManager>()
        emitAll(manager.selectedEditorFlow)
      }.stateIn(parentScope, started = SharingStarted.Eagerly, initialValue = null)
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
    if ((factory.isConfigurable && !StatusBarWidgetSettings.getInstance().isEnabled(factory)) || !factory.isAvailable(project)) {
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

      val order = StatusBarWidgetFactory.EP_NAME.filterableLazySequence().firstOrNull { it.id == factory.id }?.order
      if (order == null) {
        LOG.warn("Factory is not registered as `com.intellij.statusBarWidgetFactory` extension: ${factory.id} [2]")
        return
      }

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
    StatusBarWidgetFactory.EP_NAME.lazySequence().filterTo(result) { !isLightEditProject || it is LightEditCompatible }
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
    return factory.isAvailable(project) && factory.isConfigurable && factory.canBeEnabledOn(statusBar)
  }

  internal fun init(frame: IdeFrame): List<Pair<StatusBarWidget, LoadingOrder>> {
    val isLightEditProject = LightEdit.owns(project)
    val statusBarWidgetSettings = StatusBarWidgetSettings.getInstance()
    val availableFactories: List<Pair<StatusBarWidgetFactory, LoadingOrder>> = StatusBarWidgetFactory.EP_NAME.filterableLazySequence()
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

    pendingFactories.removeAll { (factory, _) ->
      (factory.isConfigurable && !statusBarWidgetSettings.isEnabled(factory)) || !factory.isAvailable(project)
    }

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
      override fun extensionAdded(extension: StatusBarWidgetFactory, pluginDescriptor: PluginDescriptor) {
        if (LightEdit.owns(project) && extension !is LightEditCompatible) {
          return
        }

        synchronized(widgetFactories) {
          if (widgetFactories.containsKey(extension)) {
            PluginException.logPluginError(LOG, "Factory has been added already: ${extension.id}", null, extension.javaClass)
            return
          }

          ApplicationManager.getApplication().invokeLater({
                                                            updateWidget(extension)
                                                            incModificationCount()
                                                          }, project.disposed)
        }
      }

      override fun extensionRemoved(extension: StatusBarWidgetFactory, pluginDescriptor: PluginDescriptor) {
        synchronized(widgetFactories) {
          disableWidget(extension)
          widgetFactories.remove(extension)
          incModificationCount()
        }
      }
    })

    return widgets
  }
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
  return WidgetPresentationWrapper(id = factory.id, factory = factory, dataContext = dataContext, scope = widgetScope)
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
