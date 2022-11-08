// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.wm.impl.status.widget

import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetSettings.Companion.getInstance
import com.intellij.util.ui.EdtInvocationManager
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
class StatusBarWidgetsManager(private val project: Project) : SimpleModificationTracker(), Disposable {
  companion object {
    private val LOG = logger<StatusBarWidgetsManager>()
  }

  private val pendingFactories = ArrayList<StatusBarWidgetFactory>()
  private val widgetFactories = LinkedHashMap<StatusBarWidgetFactory, StatusBarWidget?>()
  private val widgetIdsMap: MutableMap<String, StatusBarWidgetFactory> = HashMap()

  init {
    StatusBarWidgetFactory.EP_NAME.point.addExtensionPointListener(object : ExtensionPointListener<StatusBarWidgetFactory> {
      override fun extensionAdded(extension: StatusBarWidgetFactory, pluginDescriptor: PluginDescriptor) {
        addWidgetFactory(extension)
      }

      override fun extensionRemoved(extension: StatusBarWidgetFactory, pluginDescriptor: PluginDescriptor) {
        removeWidgetFactory(extension)
      }
    }, true, this)
  }

  fun updateAllWidgets() {
    synchronized(widgetFactories) {
      for (factory in widgetFactories.keys) {
        updateWidget(factory)
      }
    }
  }

  @ApiStatus.Internal
  fun disableAllWidgets() {
    synchronized(widgetFactories) {
      for (factory in widgetFactories.keys) {
        disableWidget(factory)
      }
    }
  }

  fun updateWidget(factoryExtension: Class<out StatusBarWidgetFactory?>) {
    val factory = StatusBarWidgetFactory.EP_NAME.findExtension(factoryExtension)
    synchronized(widgetFactories) {
      if (factory == null || !widgetFactories.containsKey(factory)) {
        LOG.info("Factory is not registered as `com.intellij.statusBarWidgetFactory` extension: ${factoryExtension.name}")
        return
      }
      updateWidget(factory)
    }
  }

  fun updateWidget(factory: StatusBarWidgetFactory) {
    if (factory.isAvailable(project) && (!factory.isConfigurable || getInstance().isEnabled(factory))) {
      enableWidget(factory)
    }
    else {
      disableWidget(factory)
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
    synchronized(widgetFactories) {
      for (factory in widgetFactories.keys) {
        disableWidget(factory)
      }
      widgetFactories.clear()
      pendingFactories.clear()
    }
  }

  fun findWidgetFactory(widgetId: String): StatusBarWidgetFactory? = widgetIdsMap.get(widgetId)

  fun getWidgetFactories(): Set<StatusBarWidgetFactory> = synchronized(widgetFactories) { return widgetFactories.keys }

  private fun enableWidget(factory: StatusBarWidgetFactory) {
    val availableFactories = StatusBarWidgetFactory.EP_NAME.extensionList
    synchronized(widgetFactories) {
      if (!widgetFactories.containsKey(factory)) {
        LOG.error("Factory is not registered as `com.intellij.statusBarWidgetFactory` extension: ${factory.id}")
        return
      }

      val createdWidget = widgetFactories.get(factory)
      if (createdWidget != null) {
        // widget is already enabled
        return
      }

      val widget = factory.createWidget(project)
      widgetFactories.put(factory, widget)
      widgetIdsMap.put(widget.ID(), factory)
      val anchor = getAnchor(factory, availableFactories)
      ApplicationManager.getApplication().invokeLater({
                                                        val statusBar = WindowManager.getInstance().getStatusBar(project)
                                                        if (statusBar == null) {
                                                          LOG.error("Cannot add a widget for project without root status bar: ${factory.id}")
                                                        }
                                                        else {
                                                          statusBar.addWidget(widget, anchor, this)
                                                        }
                                                      }, project.disposed)
    }
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
      val createdWidget = widgetFactories.put(factory, null)
      if (createdWidget != null) {
        widgetIdsMap.remove(createdWidget.ID())
        factory.disposeWidget(createdWidget)
        EdtInvocationManager.invokeLaterIfNeeded {
          if (!project.isDisposed) {
            WindowManager.getInstance().getStatusBar(project)?.removeWidget(createdWidget.ID())
          }
        }
      }
    }
  }

  fun canBeEnabledOnStatusBar(factory: StatusBarWidgetFactory, statusBar: StatusBar): Boolean {
    return factory.isAvailable(project) && factory.isConfigurable && factory.canBeEnabledOn(statusBar)
  }

  fun installPendingWidgets() {
    LOG.assertTrue(WindowManager.getInstance().getStatusBar(project) != null)
    synchronized(widgetFactories) {
      val pendingFactories = java.util.List.copyOf(pendingFactories)
      this.pendingFactories.clear()
      for (factory in pendingFactories) {
        addWidgetFactory(factory)
      }
    }
    updateAllWidgets()
  }

  private fun addWidgetFactory(factory: StatusBarWidgetFactory) {
    if (LightEdit.owns(project) && factory !is LightEditCompatible) {
      return
    }

    synchronized(widgetFactories) {
      if (widgetFactories.containsKey(factory)) {
        LOG.error("Factory has been added already: ${factory.id}")
        return
      }

      if (WindowManager.getInstance().getStatusBar(project) == null) {
        pendingFactories.add(factory)
        return
      }

      widgetFactories.put(factory, null)
      ApplicationManager.getApplication().invokeLater({
                                                        updateWidget(factory)
                                                        incModificationCount()
                                                      }, project.disposed)
    }
  }

  private fun removeWidgetFactory(factory: StatusBarWidgetFactory) {
    synchronized(widgetFactories) {
      disableWidget(factory)
      widgetFactories.remove(factory)
      pendingFactories.remove(factory)
      incModificationCount()
    }
  }
}