// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class)

package com.intellij.devkit.compose.preview

import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.currentCompositeKeyHashCode
import com.intellij.devkit.compose.isComposeToolingEnabled
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.platform.compose.swing.composeSwingPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.bridge.compose
import javax.swing.JComponent

internal const val TOOLWINDOW_ID = "ComposeUIPreview"

private val LOG = fileLogger()

internal class ComposePreviewToolWindowFactory : ToolWindowFactory, DumbAware {

  override fun shouldBeAvailable(project: Project): Boolean {
    return isComposeToolingEnabled()
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val wrapperPanel = ComposePreviewBusyPanel(project)

    val contentManager = toolWindow.contentManager
    val toolWindowContent = contentManager.factory.createContent(wrapperPanel, "", false)
    toolWindowContent.isCloseable = false
    contentManager.addContent(toolWindowContent)

    val actionManager = ActionManager.getInstance()
    val titleActionsGroup = actionManager.getAction("ComposePreviewActions.Title") as DefaultActionGroup
    toolWindow.setTitleActions(titleActionsGroup.getChildren(actionManager).toList())

    project.service<ComposePreviewChangesTracker>().startTracking(project, toolWindowContent) { virtualFile ->
      LOG.debug("New incoming change for preview $virtualFile")

      val oldContent = withContext(Dispatchers.UI) {
        val current = wrapperPanel.components.firstOrNull()
        wrapperPanel.setPaintBusy(true)

        current as? JComponent
      }

      var displayDefaultContent = false
      val provider = try {
        compileCode(virtualFile, project)
      }
      catch (e: CancellationException) {
        displayDefaultContent = true
        throw e
      }
      catch (e: Throwable) {
        LOG.warn("Unable to compile code for preview of $virtualFile", e)
        return@startTracking
      }
      finally {
        if (displayDefaultContent) {
          wrapperPanel.replaceContent {
            wrapperPanel.setPaintBusy(false)
            wrapperPanel.displayDefaultContent()
          }
        }
        else {
          withContext(Dispatchers.UI) {
            wrapperPanel.setPaintBusy(false)
            if (oldContent != null) wrapperPanel.setContent(oldContent)
          }
        }
      }

      if (provider == null) {
        wrapperPanel.replaceContent {
          wrapperPanel.displayUnsupportedFile()
        }
        return@startTracking
      }

      wrapperPanel.replaceContent {
        LOG.debug("Apply new UI preview for $virtualFile")

        // free up the previous content JVM classes, register new
        try {
          wrapperPanel.getUserData(PROVIDER_KEY)?.classLoader?.close()
        }
        catch (e: Exception) {
          LOG.warn("Unable to release classloader for UI preview", e)
        }
        wrapperPanel.putUserData(PROVIDER_KEY, provider)
        wrapperPanel.setPaintBusy(false)

        val content = Disposer.newDisposable(toolWindowContent, "Compose UI preview content")
        wrapperPanel.putUserData(CONTENT_KEY, content)

        try {
          wrapperPanel.setContent(when (provider.kind) {
            PreviewKind.SWING -> composeSwingPanel(content) {
              provider.build(currentComposer, currentCompositeKeyHashCode)
            }
            PreviewKind.MULTIPLATFORM -> compose(focusOnClickInside = true) {
              provider.build(currentComposer, currentCompositeKeyHashCode)
            }
          })
        }
        catch (e: ComposeLocalContextException) {
          wrapperPanel.displayMissingLocals(e)
        }
        catch (e: CancellationException) {
          wrapperPanel.displayDefaultContent()
          throw e
        }
        catch (e: Throwable) {
          LOG.error("Unable to apply content for UI preview of $virtualFile", e)
          wrapperPanel.displayError(e, virtualFile)
        }
      }
    }
  }
}

/**
 * Shows other content in the panel. Every content change goes through here, except the restore of the content on show.
 */
private suspend fun ComposePreviewBusyPanel.replaceContent(newContent: () -> Unit) {
  withContext(Dispatchers.UI) {
    // End the previous composition before its classes go. A Swing preview holds a live composition on this panel.
    // The teardown of that composition runs previewed code, which the closed class loader can no longer supply.
    getUserData(CONTENT_KEY)?.let {
      putUserData(CONTENT_KEY, null)
      Disposer.dispose(it)
    }

    newContent()
  }
}

private val PROVIDER_KEY = Key.create<ContentProvider>("ComposePreviewToolWindowFactory.ContentProvider")

/** Owns the composition the panel currently shows, so it can be torn down before the next one replaces it. */
private val CONTENT_KEY = Key.create<Disposable>("ComposePreviewToolWindowFactory.Content")
