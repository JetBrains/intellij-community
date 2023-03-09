// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.ComboBoxCompositeEditor
import com.intellij.ui.EditorTextField
import com.intellij.ui.LanguageTextField
import com.intellij.ui.TextFieldWithAutoCompletion.installCompletion
import com.intellij.ui.TextFieldWithAutoCompletionListProvider
import org.jetbrains.annotations.Nls
import javax.swing.ComboBoxModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class ComboBoxWithAutoCompletion<E : Any>(model: ComboBoxModel<E>,
                                          private val project: Project) : ComboBox<E>(model) {

  private val autoPopupController = AutoPopupController.getInstance(project)

  private val completionProvider = object : TextFieldWithAutoCompletionListProvider<E>(emptyList()) {
    override fun getLookupString(item: E) = item.toString()
  }

  private var myEditor: EditorEx? = null
  private var myEditableComponent: EditorTextField? = null

  private var selectingItem = false

  @Nls
  private var myPlaceHolder: String? = null

  init {
    isEditable = true
    initEditor()
    subscribeForModelChange(model)
  }

  override fun setSelectedItem(anObject: Any?) {
    selectingItem = true
    super.setSelectedItem(anObject)
    editor.item = anObject
    selectingItem = false
  }

  override fun getSelectedItem(): Any? {
    val text = getText()
    return if (selectingItem || text.isNullOrEmpty())
      super.getSelectedItem()
    else
      getItems().find { item -> item.toString() == text }
  }

  override fun requestFocus() {
    myEditableComponent?.requestFocus()
  }

  @Nls
  fun getText(): String? {
    return myEditor?.document?.text
  }

  fun updatePreserving(update: () -> Unit) {
    val oldItem = item
    if (oldItem != null) {
      update()
      item = oldItem
      return
    }

    val editorField = myEditableComponent
    val editor = editorField?.editor
    if (editor != null) {
      val oldText = editorField.text
      val offset = editor.caretModel.offset
      val selectionStart = editor.selectionModel.selectionStart
      val selectionStartPosition = editor.selectionModel.selectionStartPosition
      val selectionEnd = editor.selectionModel.selectionEnd
      val selectionEndPosition = editor.selectionModel.selectionEndPosition
      update()
      editorField.text = oldText
      editor.caretModel.moveToOffset(offset)
      editor.selectionModel.setSelection(selectionStartPosition, selectionStart, selectionEndPosition, selectionEnd)
      return
    }

    update()
  }

  fun setPlaceholder(@Nls placeHolder: String) {
    myPlaceHolder = placeHolder
    myEditor?.apply {
      setPlaceholder(myPlaceHolder)
    }
  }

  fun selectAll() {
    myEditableComponent?.selectAll()
  }

  fun addDocumentListener(listener: DocumentListener) {
    myEditableComponent?.addDocumentListener(listener)
  }

  fun removeDocumentListener(listener: DocumentListener) {
    myEditableComponent?.removeDocumentListener(listener)
  }

  private fun initEditor() {
    val editableComponent = createEditableComponent()

    myEditableComponent = editableComponent

    editableComponent.document.addDocumentListener(object : BulkAwareDocumentListener.Simple {
      override fun documentChanged(e: DocumentEvent) {
        if (!selectingItem && !isValueReplaced(e)) {
          showCompletion()
        }
      }
    })

    editor = ComboBoxCompositeEditor<String, EditorTextField>(editableComponent, JLabel())

    installCompletion(editableComponent.document, project, completionProvider, true)
    installForceCompletionShortCut(editableComponent)
  }

  private fun installForceCompletionShortCut(editableComponent: JComponent) {
    val completionShortcutSet = getCompletionShortcuts().firstOrNull()?.let { CustomShortcutSet(it) } ?: return

    DumbAwareAction.create {
      showCompletion()
    }.registerCustomShortcutSet(completionShortcutSet, editableComponent)
  }

  private fun showCompletion() {
    if (myEditor != null) {
      hidePopup()
      autoPopupController.scheduleAutoPopup(myEditor!!, CompletionType.BASIC) { true }
    }
  }

  private fun getCompletionShortcuts() = KeymapManager.getInstance().activeKeymap.getShortcuts(IdeActions.ACTION_CODE_COMPLETION)

  private fun createEditableComponent() = object : LanguageTextField(PlainTextLanguage.INSTANCE, project, "") {
    override fun createEditor(): EditorEx {
      myEditor = super.createEditor().apply {
        setShowPlaceholderWhenFocused(true)
        setPlaceholder(myPlaceHolder)
        SpellCheckingEditorCustomizationProvider.getInstance().disabledCustomization?.customize(this)
      }
      return myEditor!!
    }
  }

  private fun subscribeForModelChange(model: ComboBoxModel<E>) {
    model.addListDataListener(object : ListDataListener {
      override fun intervalAdded(e: ListDataEvent?) {
        completionProvider.setItems(collectItems())
      }

      override fun intervalRemoved(e: ListDataEvent?) {
        completionProvider.setItems(collectItems())
      }

      override fun contentsChanged(e: ListDataEvent?) {
        completionProvider.setItems(collectItems())
      }

      private fun collectItems(): List<E> {
        val items = mutableListOf<E>()
        for (i in 0 until model.size) {
          items += model.getElementAt(i)
        }
        return items
      }
    })
  }

  private fun isValueReplaced(e: DocumentEvent) = e.isWholeTextReplaced || isFieldCleared(e) || isItemSelected(e)

  private fun isFieldCleared(e: DocumentEvent) = e.oldLength != 0 && getCurrentText(e).isEmpty()

  private fun isItemSelected(e: DocumentEvent) = e.oldLength == 0 && getCurrentText(e).isNotEmpty() &&
                                                 getCurrentText(e) in getItems().map { it.toString() }

  private fun getCurrentText(e: DocumentEvent) = getText() ?: e.newFragment

  private fun getItems() = (0 until itemCount).map { getItemAt(it) }
}