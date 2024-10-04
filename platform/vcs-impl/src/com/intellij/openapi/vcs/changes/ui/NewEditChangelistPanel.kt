// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.Consumer
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Internal
abstract class NewEditChangelistPanel(protected val project: Project) : Wrapper() {
  private val nameTextField: EditorTextField
  private val nameComponent: ComponentWithTextFieldWrapper

  @JvmField
  protected val descriptionTextArea: EditorTextField
  private val additionalControlsPanel: JPanel = JPanel(null)
  val makeActiveCheckBox: JCheckBox
  private val consumers: MutableList<Consumer<LocalChangeList>> = mutableListOf()

  init {
    nameComponent = createComponentWithTextField(project)
    nameTextField = nameComponent.editorTextField
    nameTextField.setOneLineMode(true)
    val generateUniqueName = createNameForChangeList(project, VcsBundle.message("changes.new.changelist"))
    nameTextField.text = generateUniqueName
    nameTextField.selectAll()
    descriptionTextArea = createEditorField(project, 4)
    descriptionTextArea.setOneLineMode(false)
    makeActiveCheckBox = JCheckBox(VcsBundle.message("new.changelist.make.active.checkbox"))
    additionalControlsPanel.add(makeActiveCheckBox)
  }

  open fun init(initial: LocalChangeList?) {
    makeActiveCheckBox.isSelected = VcsConfiguration.getInstance(project).MAKE_NEW_CHANGELIST_ACTIVE
    for (support in EditChangelistSupport.EP_NAME.getExtensionList(project)) {
      support.installSearch(nameTextField, descriptionTextArea)
      ContainerUtil.addIfNotNull(consumers, support.addControls(additionalControlsPanel, initial))
    }
    nameTextField.document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        nameChangedImpl(initial)
      }
    })
    nameChangedImpl(initial)
    setContent(buildMainPanel())
    validate()
    repaint()
  }

  private fun buildMainPanel() = panel {
    val gap = 3
    row(VcsBundle.message("edit.changelist.name")) {
      cell(nameComponent.myComponent)
        .resizableColumn()
        .align(AlignX.FILL)
        .applyToComponent {
          putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGaps(gap))
        }
    }.bottomGap(BottomGap.SMALL)

    row {
      label(VcsBundle.message("edit.changelist.description"))
        .align(AlignY.TOP)
        .gap(RightGap.SMALL)

      cell(descriptionTextArea)
        .resizableColumn()
        .align(Align.FILL)
        .customize(UnscaledGaps(left = gap, right = gap))
    }.resizableRow()
      .layout(RowLayout.PARENT_GRID)
      .bottomGap(BottomGap.SMALL)

    row {
      additionalControlsPanel.components.forEach {
        cell(it as JComponent)
      }
    }
  }


  protected open fun nameChangedImpl(initial: LocalChangeList?) {
    val name = changeListName
    if (name.isBlank()) {
      nameChanged(VcsBundle.message("new.changelist.empty.name.error"))
    }
    else if ((initial == null || name != initial.name) && ChangeListManager.getInstance(project).findChangeList(name) != null) {
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

  protected abstract class ComponentWithTextFieldWrapper(val myComponent: JComponent) {
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
          editor.contentComponent.border = JBUI.Borders.empty(3, 5)
        }
        // set the min and pref sizes for the editor field to stop the internal sizing logic and rely on parent component layout
        editorField.minimumSize = JBDimension(200, 1)
        editorField.preferredSize = JBDimension(200, 1)
      }
      return editorField
    }
  }
}