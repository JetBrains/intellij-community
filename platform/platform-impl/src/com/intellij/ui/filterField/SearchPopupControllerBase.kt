package com.intellij.ui.filterField

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.CollectionListModel
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.event.KeyEvent
import javax.swing.JList

abstract class SearchPopupControllerBase(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
  private val searchTextField: FilterSearchTextField,
  private val list: JList<*>
) {
  private var searchPopup: FilterSearchPopup? = null

  fun handleShowPopup() {
    val query = searchTextField.text
    val length = query.length
    val position = caretPosition
    if (position < length) {
      if (query[position] != ' ' && query[position] != ',') {
        handleShowPopupForQuery()
        return
      }
    }
    val completionPosition = SearchQueryParserBase.parseAttributeInQuery(query, position)
    if (completionPosition.attributeValue == null) {
      showAttributesPopup(completionPosition.attributeName, completionPosition.startPosition)
    }
    else {
      handleShowAttributeValuesPopup(completionPosition.attributeName, completionPosition.attributeValue,
                                     completionPosition.startPosition)
    }
  }

  private val caretPosition: Int
    get() = searchTextField.textEditor.caretPosition

  protected abstract fun getAttributes(): List<String>

  fun showAttributesPopup(namePrefix: String?, caretPosition: Int) {
    val model = CollectionListModel(getAttributes())

    if (noPrefixSearchValues(model, namePrefix)) {
      return
    }

    val async = searchPopup != null
    if (updatePopupOrCreate(FilterCompletionPopupType.ATTRIBUTE_NAME, model, namePrefix, caretPosition)) {
      return
    }

    createAndShow(async, object : SearchPopupCallback(namePrefix) {
      override fun accept(value: String?) {
        if (value == null) return
        appendSearchText(value, prefix)
        handleShowAttributeValuesPopup(value, null, searchTextField.textEditor.caretPosition)
      }
    })
  }

  private fun handleShowAttributeValuesPopup(name: String, valuePrefix: String?, caretPosition: Int) {
    coroutineScope.launch {
      val values = smartReadAction(project) {
        getCompletionValues(name)
      }

      withContext(Dispatchers.EDT) {
        if (values.isEmpty()) {
          handleShowPopupForQuery()
          return@withContext
        }

        val model = CollectionListModel(values)
        if (noPrefixSearchValues(model, valuePrefix)) {
          return@withContext
        }
        if (updatePopupOrCreate(FilterCompletionPopupType.ATTRIBUTE_VALUE, model, valuePrefix, caretPosition)) {
          return@withContext
        }
        createAndShow(true, object : SearchPopupCallback(valuePrefix) {
          override fun accept(value: String?) {
            if (value == null) return
            appendSearchText(SearchQueryParserBase.wrapAttribute(value), prefix)
            handleShowPopupForQuery()
          }
        })
      }
    }
  }

  private fun updatePopupOrCreate(type: FilterCompletionPopupType,
                                  model: CollectionListModel<String>,
                                  prefix: String?,
                                  caretPosition: Int): Boolean {
    val currentPopup = searchPopup

    if (currentPopup == null || currentPopup.completionPlace !== type || !currentPopup.isValid) {
      createPopup(type, model, caretPosition)
    }
    else {
      currentPopup.model.replaceAll(model.items)
      if (model.size > 0) {
        currentPopup.list?.selectedIndex = 0
      }
      val callback = currentPopup.callback
      if (callback != null) {
        callback.prefix = prefix
      }
      currentPopup.caretPosition = caretPosition
      currentPopup.update()
      return true
    }

    return false
  }

  private fun createPopup(type: FilterCompletionPopupType, model: CollectionListModel<String>, caretPosition: Int) {
    hidePopup()
    searchPopup = FilterSearchPopup(searchTextField, object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        searchPopup = null
      }
    }, type, model, caretPosition)
  }

  private fun createAndShow(async: Boolean, callback: SearchPopupCallback) {
    searchPopup?.createAndShow(callback, async)
  }

  private fun noPrefixSearchValues(model: CollectionListModel<String>, prefix: String?): Boolean {
    if (prefix.isNullOrBlank()) {
      return false
    }
    var index = 0
    while (index < model.size) {
      val attribute = model.getElementAt(index)
      if (attribute == prefix) {
        hidePopup()
        return true
      }
      if (StringUtil.startsWithIgnoreCase(attribute, prefix)) {
        index++
      }
      else {
        model.remove(index)
      }
    }
    if (model.isEmpty) {
      handleShowPopupForQuery()
      return true
    }
    return false
  }

  @RequiresBackgroundThread
  @RequiresReadLock
  protected abstract fun getCompletionValues(attribute: String): List<String>

  private fun handleShowPopupForQuery() {
    hidePopup()
  }

  val isPopupShow: Boolean
    get() = searchPopup != null && searchPopup!!.isValid

  fun hidePopup() {
    searchPopup?.hide()
    searchPopup = null
  }

  private fun appendSearchText(originalValue: String, prefix: String?) {
    var value = originalValue
    if (getAttributes().contains(value)) {
      value += " "
    }

    var text = searchTextField.text
    var suffix = ""
    val position = caretPosition

    if (searchPopup != null) {
      searchPopup!!.skipCaretEvent = true
    }

    if (position < text.length) {
      suffix = text.substring(position)
      text = text.substring(0, position)
    }

    if (prefix == null) {
      searchTextField.setTextIgnoreEvents(text + value + suffix)
    }
    else if (StringUtil.startsWithIgnoreCase(value, prefix) || StringUtil.startsWithIgnoreCase(value, "\"" + prefix)) {
      searchTextField.setTextIgnoreEvents(text.substring(0, text.length - prefix.length) + value + suffix)
    }
    else {
      searchTextField.setTextIgnoreEvents(text + value + suffix)
    }

    searchTextField.textEditor.caretPosition = searchTextField.text.length - suffix.length
  }

  fun handleEnter(event: KeyEvent): Boolean {
    val popupList = searchPopup?.list

    if (popupList != null && popupList.selectedIndex != -1) {
      popupList.dispatchEvent(event)
      return true
    }
    return false
  }

  fun handleUpDown(event: KeyEvent): Boolean {
    val popupList = searchPopup?.list

    if (popupList != null) {
      if (event.keyCode == KeyEvent.VK_DOWN
          && popupList.selectedIndex == -1) {
        popupList.setSelectedIndex(0)
      }
      else {
        popupList.dispatchEvent(event)
      }
      return true
    }
    list.dispatchEvent(event)
    return true
  }
}