// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.createNameForChangeList
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.*
import com.intellij.util.Consumer
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.border.CompoundBorder

abstract class NewEditChangelistPanel(protected val myProject: Project) : JPanel(GridBagLayout()) {
  private val nameTextField: EditorTextField
  @JvmField
  protected val descriptionTextArea: EditorTextField
  private val additionalControlsPanel: JPanel
  val makeActiveCheckBox: JCheckBox
  private val consumers: MutableList<Consumer<LocalChangeList>> = ArrayList()

  init {
    val gb = GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                JBUI.insets(1), 0, 0)
    val nameLabel = JLabel(VcsBundle.message("edit.changelist.name"))
    add(nameLabel, gb)
    ++gb.gridx
    gb.fill = GridBagConstraints.HORIZONTAL
    gb.weightx = 1.0
    val componentWithTextField = createComponentWithTextField(myProject)
    nameTextField = componentWithTextField.editorTextField
    nameTextField.setOneLineMode(true)
    val generateUniqueName = createNameForChangeList(myProject, VcsBundle.message("changes.new.changelist"))
    nameTextField.text = generateUniqueName
    nameTextField.selectAll()
    add(componentWithTextField.myComponent, gb)
    nameLabel.labelFor = nameTextField
    ++gb.gridy
    gb.gridx = 0
    gb.weightx = 0.0
    gb.fill = GridBagConstraints.NONE
    gb.anchor = GridBagConstraints.NORTHWEST
    val commentLabel = JLabel(VcsBundle.message("edit.changelist.description"))
    UIUtil.addInsets(commentLabel, JBUI.insetsRight(4))
    add(commentLabel, gb)
    ++gb.gridx
    gb.weightx = 1.0
    gb.weighty = 1.0
    gb.fill = GridBagConstraints.BOTH
    gb.insets = JBUI.insetsTop(2)
    descriptionTextArea = createEditorField(myProject, 4)
    descriptionTextArea.setOneLineMode(false)
    add(descriptionTextArea, gb)
    commentLabel.labelFor = descriptionTextArea
    gb.insets = JBUI.insetsTop(0)
    ++gb.gridy
    gb.gridx = 0
    gb.gridwidth = 2
    gb.weighty = 0.0
    additionalControlsPanel = JPanel()
    val layout = BoxLayout(additionalControlsPanel, BoxLayout.X_AXIS)
    additionalControlsPanel.layout = layout
    makeActiveCheckBox = JCheckBox(VcsBundle.message("new.changelist.make.active.checkbox"))
    makeActiveCheckBox.border = JBUI.Borders.emptyRight(4)
    additionalControlsPanel.add(makeActiveCheckBox)
    add(additionalControlsPanel, gb)
  }

  open fun init(initial: LocalChangeList?) {
    makeActiveCheckBox.isSelected = VcsConfiguration.getInstance(myProject).MAKE_NEW_CHANGELIST_ACTIVE
    for (support in EditChangelistSupport.EP_NAME.getExtensions(myProject)) {
      support.installSearch(nameTextField, descriptionTextArea)
      ContainerUtil.addIfNotNull(consumers, support.addControls(additionalControlsPanel, initial))
    }
    nameTextField.document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        nameChangedImpl(myProject, initial)
      }
    })
    nameChangedImpl(myProject, initial)
  }

  protected open fun nameChangedImpl(project: Project?, initial: LocalChangeList?) {
    val name = changeListName
    if (name == null || name.isBlank()) {
      nameChanged(VcsBundle.message("new.changelist.empty.name.error"))
    }
    else if ((initial == null || name != initial.name) && ChangeListManager.getInstance(
        project!!).findChangeList(name) != null) {
      nameChanged(VcsBundle.message("new.changelist.duplicate.name.error"))
    }
    else {
      nameChanged(null)
    }
  }

  fun changelistCreatedOrChanged(list: LocalChangeList) {
    for (consumer in consumers) consumer.consume(list)
  }

  var changeListName: String by nameTextField::text

  var description: String?
    get() = descriptionTextArea.text
    set(s) {
      descriptionTextArea.setText(s)
    }
  val content: JComponent
    get() = this

  override fun requestFocus() {
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown { IdeFocusManager.getGlobalInstance().requestFocus(nameTextField, true) }
  }

  val preferredFocusedComponent: JComponent
    get() = nameTextField

  protected abstract fun nameChanged(errorMessage: @Nls String?)
  protected open fun createComponentWithTextField(project: Project): ComponentWithTextFieldWrapper {
    val editorTextField = createEditorField(project, 1)
    return object : ComponentWithTextFieldWrapper(editorTextField) {
      override val editorTextField: EditorTextField
        get() = editorTextField
    }
  }

  protected abstract class ComponentWithTextFieldWrapper(val myComponent: Component) {
    abstract val editorTextField: EditorTextField
  }

  companion object {
    private fun createEditorField(project: Project, defaultLines: Int): EditorTextField {
      val editorFeatures: MutableSet<EditorCustomization> = HashSet()
      ContainerUtil.addIfNotNull(editorFeatures, SpellCheckingEditorCustomizationProvider.getInstance().enabledCustomization)
      if (defaultLines == 1) {
        editorFeatures.add(HorizontalScrollBarEditorCustomization.DISABLED)
        editorFeatures.add(OneLineEditorCustomization.ENABLED)
      }
      else {
        editorFeatures.add(SoftWrapsEditorCustomization.ENABLED)
      }
      val editorField = EditorTextFieldProvider.getInstance().getEditorField(FileTypes.PLAIN_TEXT.language, project, editorFeatures)
      if (defaultLines > 1) {
        editorField.addSettingsProvider { editor: EditorEx ->
          editor.contentComponent.border = CompoundBorder(editor.contentComponent.border, JBUI.Borders.empty(2, 5))
        }
      }
      return editorField
    }
  }
}