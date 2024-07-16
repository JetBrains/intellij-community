package com.intellij.ui.filterField

import com.intellij.ide.plugins.newui.EventHandler
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.ui.SearchTextField
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.util.function.Consumer

open class FilterSearchTextField : SearchTextField() {
  var isSkipDocumentEvents: Boolean = false
    private set

  private var focusRegistration: Runnable? = null

  override fun addNotify() {
    super.addNotify()

    textEditor.addFocusListener(object : FocusListener {
      override fun focusGained(e: FocusEvent) {
        if (focusRegistration == null) {
          focusRegistration = EventHandler.addGlobalAction(textEditor, IdeActions.ACTION_CODE_COMPLETION) {
            showCompletionPopup()
          }
        }
      }

      override fun focusLost(e: FocusEvent) {
        if (rootPane != null) { // if still attached to UI
          focusRegistration?.run()
          focusRegistration = null
        }
      }
    })
  }

  override fun removeNotify() {
    super.removeNotify()

    focusRegistration?.run()
    focusRegistration = null
  }

  protected open fun showCompletionPopup() {
  }

  override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
    super.setBounds(x - 1, y, width + 2, height)
  }

  override fun setEmptyHistory() {
    history = emptyList()
  }

  override fun setSelectedItem(s: String) {
  }

  fun setTextIgnoreEvents(text: String?) {
    try {
      isSkipDocumentEvents = true
      setText(text)
    }
    finally {
      isSkipDocumentEvents = false
    }
  }
}

internal enum class FilterCompletionPopupType {
  ATTRIBUTE_NAME, ATTRIBUTE_VALUE
}

internal abstract class SearchPopupCallback(var prefix: String?) : Consumer<String?>