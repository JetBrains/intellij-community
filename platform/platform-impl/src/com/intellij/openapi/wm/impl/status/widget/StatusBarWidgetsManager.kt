// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.openapi.wm.impl.status.createComponentByWidgetPresentation
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.swing.JComponent

private val LOG = logger<StatusBarWidgetsManager>()

@Service(Service.Level.PROJECT)
class StatusBarWidgetsManager(
  private val project: Project,
  private val parentScope: CoroutineScope,
) : SimpleModificationTracker(), Disposable {
  private val widgetFactories = LinkedHashMap<StatusBarWidgetFactory, StatusBarWidget>()
  private val widgetIdMap = HashMap<String, StatusBarWidgetFactory>()

  init {
    StatusBarActionManager.getInstance()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  internal val dataContext: WidgetPresentationDataContext = object : WidgetPresentationDataContext {
    override val project: Project
      get() = this@StatusBarWidgetsManager.project

    override val currentFileEditor: StateFlow<FileEditor?> by lazy {
      flow { emit(project.serviceAsync<FileEditorManager>() as FileEditorManagerEx) }
        .take(1)
        .flatMapConcat { it.currentFileEditorFlow }
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

  fun updateWidget(factory: StatusBarWidgetFactory) {
    if ((factory.isConfigurable && !StatusBarWidgetSettings.getInstance().isEnabled(factory)) || !factory.isAvailable(project)) {
      disableWidget(factory)
      return
    }

    synchronized(widgetFactories) {
      if (widgetFactories.containsKey(factory)) {
        return  // this widget is already enabled
      }

      val order = StatusBarWidgetFactory.EP_NAME.filterableLazySequence().firstOrNull { it.id == factory.id }?.order
      if (order == null) {
        LOG.warn("Factory is not registered as `com.intellij.statusBarWidgetFactory` extension: ${factory.id} [2]")
        return
      }

      val widget = createWidget(factory, dataContext, parentScope)
      widgetFactories[factory] = widget
      widgetIdMap[widget.ID()] = factory
      parentScope.launch(Dispatchers.EDT) {
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

  fun wasWidgetCreated(factoryId: String): Boolean =
    synchronized(widgetFactories) {
      widgetFactories.keys.any { it.id.equals(factoryId, ignoreCase = true) }
    }

  override fun dispose() {
    parentScope.cancel()
  }

  fun findWidgetFactory(widgetId: String): StatusBarWidgetFactory? = widgetIdMap[widgetId]

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

  fun canBeEnabledOnStatusBar(factory: StatusBarWidgetFactory, statusBar: StatusBar): Boolean =
    factory.isAvailable(project) && factory.isConfigurable && factory.canBeEnabledOn(statusBar)

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

    StatusBarWidgetFactory.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<StatusBarWidgetFactory> {
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
    }, this)

    return widgets
  }

  private fun createWidget(
    factory: StatusBarWidgetFactory,
    dataContext: WidgetPresentationDataContext,
    parentScope: CoroutineScope,
  ): StatusBarWidget =
    if (factory !is WidgetPresentationFactory) factory.createWidget(dataContext.project, parentScope)
    else object : StatusBarWidget, CustomStatusBarWidget {
      private val scope = lazy { parentScope.childScope(name = "${factory.id}-widget-scope") }

      override fun ID(): String = factory.id

      override fun getComponent(): JComponent {
        val scope = scope.value
        return createComponentByWidgetPresentation(factory.createPresentation(dataContext, scope), dataContext.project, scope)
      }

      override fun dispose() {
        if (scope.isInitialized()) {
          scope.value.cancel()
        }
      }
    }
}
