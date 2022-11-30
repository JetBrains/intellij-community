// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.wm.impl.status.widget

import com.intellij.diagnostic.runActivity
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
class StatusBarWidgetsManager(private val project: Project) : SimpleModificationTracker(), Disposable {
  companion object {
    private val LOG = logger<StatusBarWidgetsManager>()
  }

  private val widgetFactories = LinkedHashMap<StatusBarWidgetFactory, StatusBarWidget?>()
  private val widgetIdMap = HashMap<String, StatusBarWidgetFactory>()

  init {
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosed(project: Project) {
        // remove all widgets - frame maybe reused for another project
        // must be not as a part of dispose, because statusBar will be null
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        synchronized(widgetFactories) {
          for ((factory, widget) in widgetFactories) {
            val key = (widget ?: continue).ID()
            factory.disposeWidget(widget)
            statusBar?.removeWidget(key)
          }
          widgetFactories.clear()
          widgetIdMap.clear()
        }
      }
    })
  }

  fun updateAllWidgets() {
    synchronized(widgetFactories) {
      for (factory in widgetFactories.keys.toList()) {
        updateWidget(factory)
      }
    }
  }

  @ApiStatus.Internal
  fun disableAllWidgets() {
    synchronized(widgetFactories) {
      for (factory in widgetFactories.keys.toList()) {
        disableWidget(factory)
      }
    }
  }

  fun updateWidget(factoryExtension: Class<out StatusBarWidgetFactory>) {
    val factory = StatusBarWidgetFactory.EP_NAME.findExtension(factoryExtension)
    synchronized(widgetFactories) {
      if (factory == null) {
        LOG.info("Factory is not registered as `com.intellij.statusBarWidgetFactory` extension: ${factoryExtension.name}")
      }
      else {
        updateWidget(factory)
      }
    }
  }

  fun updateWidget(factory: StatusBarWidgetFactory) {
    if (!(!factory.isConfigurable || StatusBarWidgetSettings.getInstance().isEnabled(factory)) || !factory.isAvailable(project)) {
      disableWidget(factory)
      return
    }

    synchronized(widgetFactories) {
      val createdWidget = widgetFactories.get(factory)
      if (createdWidget != null) {
        // widget is already enabled
        return
      }

      val widget = factory.createWidget(project)
      widgetFactories.put(factory, widget)
      widgetIdMap.put(widget.ID(), factory)
      val anchor = getAnchor(factory = factory, availableFactories = widgetFactories.keys.toList())
      @Suppress("DEPRECATION")
      project.coroutineScope.launch(Dispatchers.EDT) {
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        if (statusBar == null) {
          LOG.error("Cannot add a widget for project without root status bar: ${factory.id}")
        }
        else {
          if (statusBar is IdeStatusBarImpl) {
            statusBar.addRightWidget(widget, anchor)
          }
          else {
            @Suppress("DEPRECATION", "removal")
            statusBar.addWidget(widget, anchor)
          }
        }
      }
    }
  }

  fun wasWidgetCreated(factory: StatusBarWidgetFactory?): Boolean {
    synchronized(widgetFactories) { return widgetFactories.get(factory) != null }
  }

  fun wasWidgetCreated(factoryId: String): Boolean {
    synchronized(widgetFactories) {
      return widgetFactories.keys.any { factory ->
        factory.id.equals(factoryId, ignoreCase = true)
      }
    }
  }

  override fun dispose() {
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

  private fun getAnchor(factory: StatusBarWidgetFactory, availableFactories: List<StatusBarWidgetFactory>): String {
    if (factory is StatusBarWidgetProviderToFactoryAdapter) {
      return factory.anchor
    }

    val indexOf = availableFactories.indexOf(factory)
    for (i in indexOf + 1 until availableFactories.size) {
      val nextFactory = availableFactories[i]
      val widget = widgetFactories.get(nextFactory)
      if (widget != null) {
        return StatusBar.Anchors.before(widget.ID())
      }
    }
    for (i in indexOf - 1 downTo 0) {
      val prevFactory = availableFactories[i]
      val widget = widgetFactories.get(prevFactory)
      if (widget != null) {
        return StatusBar.Anchors.after(widget.ID())
      }
    }
    return StatusBar.Anchors.DEFAULT_ANCHOR
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

  suspend fun init(statusBarSupplier: () -> StatusBar) {
    val isLightEditProject = LightEdit.owns(project)
    val statusBarWidgetSettings = StatusBarWidgetSettings.getInstance()
    val availableFactories = StatusBarWidgetFactory.EP_NAME.filterableLazySequence()
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
      .mapNotNull { it.instance }
      .filter { !isLightEditProject || it is LightEditCompatible }
      .toList()

    val widgets = synchronized(widgetFactories) {
      val pendingFactories = availableFactories.toMutableList()
      @Suppress("removal", "DEPRECATION")
      StatusBarWidgetProvider.EP_NAME.extensionList.mapTo(pendingFactories) { StatusBarWidgetProviderToFactoryAdapter(project, it) }

      val result = mutableListOf<Pair<StatusBarWidget, String>>()
      for (factory in pendingFactories) {
        if ((factory.isConfigurable && !statusBarWidgetSettings.isEnabled(factory)) || !factory.isAvailable(project)) {
          continue
        }

        if (widgetFactories.containsKey(factory)) {
          LOG.error("Factory has been added already: ${factory.id}")
          continue
        }

        val widget = factory.createWidget(project)
        widgetFactories.put(factory, widget)
        widgetIdMap.put(widget.ID(), factory)
        result.add(widget to getAnchor(factory = factory, availableFactories = availableFactories))
      }
      result
    }

    incModificationCount()

    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      val statusBar = statusBarSupplier()
      runActivity("status bar widgets adding") {
        if (statusBar is IdeStatusBarImpl) {
          for ((widget, anchor) in widgets) {
            statusBar.addRightWidget(widget, anchor)
          }
        }
        else {
          for ((widget, anchor) in widgets) {
            @Suppress("DEPRECATION", "removal")
            statusBar.addWidget(widget, anchor)
          }
        }
      }
    }

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

          widgetFactories.put(extension, null)
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
  }
}