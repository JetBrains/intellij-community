// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.sandbox

import com.intellij.devkit.compose.DevkitComposeBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.FileIconProvider
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.ui.JBUI
import org.jetbrains.idea.devkit.util.PsiUtil
import org.jetbrains.jewel.bridge.compose
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.*

internal class ComposeSandboxAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null && PsiUtil.isPluginProject(e.project!!)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    // Create and open a light virtual file that our provider will recognize
    val file = ComposeSandboxVirtualFile()
    FileEditorManager.getInstance(project).openFile(file, true)
  }
}

internal class ComposeSandboxVirtualFile() : LightVirtualFile("Compose Sandbox") {
  init {
    isWritable = false
  }
}

internal const val REOPEN_ON_START_PROPERTY = "compose.sandbox.show.automatically.on.project.open"

internal class ComposeSandboxFileEditorProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean = file is ComposeSandboxVirtualFile

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val composeComponent: JComponent = compose {
      ComposeSandbox() // <<-- implement all demos there
    }
    val panel = JPanel(BorderLayout())
    panel.add(composeComponent, BorderLayout.CENTER)

    val checkbox = JCheckBox(DevkitComposeBundle.message("compose.sandbox.show.automatically.on.project.open"))
    checkbox.isSelected = PropertiesComponent.getInstance(project).getBoolean(REOPEN_ON_START_PROPERTY, false)
    checkbox.isContentAreaFilled = false
    checkbox.horizontalAlignment = SwingConstants.LEADING
    checkbox.addActionListener {
      PropertiesComponent.getInstance(project).setValue(REOPEN_ON_START_PROPERTY, checkbox.isSelected, false)
    }

    val bottomPanel = JPanel()
    bottomPanel.border = JBUI.Borders.emptyBottom(10)
    bottomPanel.layout = BoxLayout(bottomPanel, BoxLayout.X_AXIS)
    bottomPanel.add(JPanel())
    bottomPanel.add(checkbox)
    bottomPanel.add(JPanel())
    panel.add(bottomPanel, BorderLayout.SOUTH)

    return ComposeSandboxFileEditor(panel, file)
  }

  override fun getEditorTypeId(): String = "compose-sandbox-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

private class ComposeSandboxFileEditor(private val component: JComponent, private val fakeFile: VirtualFile)
  : UserDataHolderBase(), FileEditor {

  override fun getComponent(): JComponent = component
  override fun getPreferredFocusedComponent(): JComponent = component
  override fun getFile(): VirtualFile = fakeFile

  override fun getName(): String = DevkitComposeBundle.message("compose.sandbox")
  override fun setState(state: FileEditorState) {}
  override fun isModified(): Boolean = false
  override fun isValid(): Boolean = true

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
  override fun dispose() {
    // do nothing
  }
}

/**
 * Provides a specific icon for the ComposeSandboxVirtualFile displayed in editors and views.
 */
internal class ComposeSandboxFileIconProvider : FileIconProvider {
  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
    return if (file is ComposeSandboxVirtualFile) AllIcons.FileTypes.UiForm else null
  }
}
