// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "LiftReturnOrAssignment")

package com.intellij.openapi.wm.impl.status.widget

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
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.swing.JComponent

@Service(Service.Level.PROJECT)
class StatusBarWidgetsManager(private val project: Project,
                              private val parentScope: CoroutineScope) : SimpleModificationTracker(), Disposable {
  companion object {
    private val LOG = logger<StatusBarWidgetsManager>()

    internal fun anchorToOrder(anchor: String): LoadingOrder {
      if (anchor.isEmpty() || anchor.equals("any", ignoreCase = true)) {
        return LoadingOrder.ANY
      }
      else {
        try {
          return LoadingOrder(anchor)
        }
        catch (e: Throwable) {
          LOG.error("Cannot parse anchor ${anchor}", e)
          return LoadingOrder.ANY
        }
      }
    }
  }

  private val widgetFactories = LinkedHashMap<StatusBarWidgetFactory, StatusBarWidget>()
  private val widgetIdMap = HashMap<String, StatusBarWidgetFactory>()

  internal val dataContext: WidgetPresentationDataContext = object : WidgetPresentationDataContext {
    override val project: Project
      get() = this@StatusBarWidgetsManager.project

    override val currentFileEditor: StateFlow<FileEditor?> by lazy {
      flow {
        emit(project.serviceAsync<FileEditorManager>() as FileEditorManagerEx)
      }
        .take(1)
        .flatMapConcat { it.currentFileEditorFlow }
        .stateIn(scope = parentScope, started = SharingStarted.WhileSubscribed(), initialValue = null)
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
      LOG.warn("Factory is not registered as `com.intellij.statusBarWidgetFactory` extension: ${factoryExtension.name}")
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
        // this widget is already enabled
        return
      }

      val order = StatusBarWidgetFactory.EP_NAME.filterableLazySequence().firstOrNull { it.id == factory.id }?.order
      if (order == null) {
        LOG.warn("Factory is not registered as `com.intellij.statusBarWidgetFactory` extension: ${factory.id}")
        return
      }

      val widget = createWidget(factory = factory, dataContext, parentScope = parentScope)
      widgetFactories.put(factory, widget)
      widgetIdMap.put(widget.ID(), factory)
      parentScope.launch(Dispatchers.EDT) {
        when (val statusBar = WindowManager.getInstance().getStatusBar(project)) {
          null -> LOG.error("Cannot add a widget for project without root status bar: ${factory.id}")
          is IdeStatusBarImpl -> statusBar.addWidget(widget, order)
          else -> {
            @Suppress("DEPRECATION")
            statusBar.addWidget(widget, order.toString())
          }
        }
      }
    }
  }

  fun wasWidgetCreated(factoryId: String): Boolean {
    synchronized(widgetFactories) {
      return widgetFactories.keys.any { factory ->
        factory.id.equals(factoryId, ignoreCase = true)
      }
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
          LOG.warn("${it.implementationClassName} doesn't define id for extension (point=com.intellij.statusBarWidgetFactory). " +
                   "Please specify `id` attribute.")
          true
        }
        else {
          !statusBarWidgetSettings.isExplicitlyDisabled(id)
        }
      }
      .mapNotNull { (it.instance ?: return@mapNotNull null) to it.order }
      .filter { !isLightEditProject || it.first is LightEditCompatible }
      .toList()

    val widgets: List<Pair<StatusBarWidget, LoadingOrder>> = synchronized(widgetFactories) {
      val pendingFactories = availableFactories.toMutableList()
      @Suppress("removal", "DEPRECATION")
      StatusBarWidgetProvider.EP_NAME.extensionList.mapTo(pendingFactories) {
        StatusBarWidgetProviderToFactoryAdapter(project, frame, it) to anchorToOrder(it.anchor)
      }

      val result = mutableListOf<Pair<StatusBarWidget, LoadingOrder>>()
      for ((factory, anchor) in pendingFactories) {
        if ((factory.isConfigurable && !statusBarWidgetSettings.isEnabled(factory)) || !factory.isAvailable(project)) {
          continue
        }

        if (widgetFactories.containsKey(factory)) {
          LOG.error("Factory has been added already: ${factory.id}")
          continue
        }

        val widget = createWidget(factory = factory, dataContext = dataContext, parentScope = parentScope)
        widgetFactories.put(factory, widget)
        widgetIdMap.put(widget.ID(), factory)
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
            LOG.error("Factory has been added already: ${extension.id}")
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
}

private fun createWidget(factory: StatusBarWidgetFactory,
                         dataContext: WidgetPresentationDataContext,
                         parentScope: CoroutineScope): StatusBarWidget {
  if (factory !is WidgetPresentationFactory) {
    return factory.createWidget(dataContext.project, parentScope)
  }

  return object : StatusBarWidget, CustomStatusBarWidget {
    private val scope = lazy { parentScope.childScope() }

    override fun ID(): String = factory.id

    override fun getComponent(): JComponent {
      val scope = scope.value
      return createComponentByWidgetPresentation(presentation = factory.createPresentation(context = dataContext, scope = scope),
                                                 project = dataContext.project,
                                                 scope = scope)
    }


    override fun dispose() {
      if (scope.isInitialized()) {
        scope.value.cancel()
      }
    }
  }
}

