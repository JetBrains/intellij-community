// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.toolWindow.ToolWindowPane
import com.intellij.ui.ClientProperty
import com.intellij.ui.components.JBBox
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import javax.swing.JComponent

private val EXTENSION_KEY = Key.create<String>("extensionKey")

/**
 * Helper class which sets up a main IDE frame
 */
internal class IdeProjectFrameHelper(
  frame: IdeFrameImpl,
  loadingState: FrameLoadingState,
) : ProjectFrameHelper(frame, loadingState) {
  @get:RequiresEdt
  lateinit var toolWindowPane: ToolWindowPane
    private set

  private var northPanel: JBBox? = null

  override fun createCenterComponent(): JComponent {
    val paneId = WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
    val pane = ToolWindowPane.create(frame, cs, paneId)
    toolWindowPane = pane
    return pane.buttonManager.wrapWithControls(pane)
  }

  override suspend fun setProject(project: Project) {
    installNorthComponents(project)
    super.setProject(project)
  }

  private suspend fun installNorthComponents(project: Project) {
    val northExtensions = IdeRootPaneNorthExtension.EP_NAME.extensionList
    if (northExtensions.isEmpty()) {
      return
    }

    val northPanel = withContext(Dispatchers.EDT) {
      JBBox.createVerticalBox().also {
        contentPane.add(it, BorderLayout.NORTH)
        northPanel = it
      }
    }

    for (extension in northExtensions) {
      val flow = extension.component(project = project, isDocked = false, statusBar = statusBar!!)
      val key = extension.key
      if (flow != null) {
        cs.launch(ModalityState.any().asContextElement()) {
          flow.collect(FlowCollector { component ->
            withContext(Dispatchers.EDT) {
              if (component == null) {
                val count = northPanel.componentCount
                for (i in count - 1 downTo 0) {
                  val c = northPanel.getComponent(i)
                  if (ClientProperty.isSet(c, EXTENSION_KEY, key)) {
                    northPanel.remove(i)
                    break
                  }
                }
              }
              else {
                ClientProperty.put(component, EXTENSION_KEY, key)
                northPanel.add(component)
              }
            }
          })
        }
        continue
      }

      withContext(Dispatchers.EDT) {
        extension.createComponent(project, isDocked = false)?.let {
          ClientProperty.put(it, EXTENSION_KEY, key)
          northPanel.add(it)
        }
      }
    }
  }

  override fun updateContentComponents() {
    northPanel?.revalidate()
  }

  override fun getNorthExtension(key: String): JComponent? =
    northPanel?.components?.firstOrNull { ClientProperty.isSet(it, EXTENSION_KEY, key) } as? JComponent
}