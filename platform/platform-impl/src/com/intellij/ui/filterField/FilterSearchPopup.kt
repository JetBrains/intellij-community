package com.intellij.ui.filterField

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JList
import javax.swing.SwingUtilities
import javax.swing.event.CaretEvent
import javax.swing.event.CaretListener
import javax.swing.text.BadLocationException

internal class FilterSearchPopup(searchTextField: SearchTextField,
                                 private val listener: JBPopupListener,
                                 val completionPlace: FilterCompletionPopupType,
                                 val model: CollectionListModel<String>,
                                 var caretPosition: Int) : ComponentAdapter(), CaretListener {

  private val editor: JBTextField = searchTextField.textEditor
  private var popup: JBPopup? = null
  private var event: LightweightWindowEvent? = null
  private var dialogComponent: Component? = null

  var list: JList<String>? = null
  var callback: SearchPopupCallback? = null
  var skipCaretEvent: Boolean = false
  var data: Any? = null

  fun createAndShow(callback: SearchPopupCallback, async: Boolean) {
    this.callback = callback

    val renderer: ColoredListCellRenderer<String> = object : ColoredListCellRenderer<String>() {
      override fun customizeCellRenderer(list: JList<out String>, value: @NlsSafe String?,
                                         index: Int, selected: Boolean, hasFocus: Boolean) {
        append(value ?: "")
      }
    }

    val ipad = renderer.ipad
    ipad.right = getXOffset()
    ipad.left = ipad.right
    renderer.font = editor.font

    @Suppress("DEPRECATION")
    val popup = JBPopupFactory.getInstance().createListPopupBuilder(JBList(model).also { list = it })
      .setMovable(false)
      .setResizable(false)
      .setRequestFocus(false)
      .setItemChosenCallback(callback::accept)
      .setFont(editor.font)
      .setRenderer(renderer)
      .createPopup()

    popup.addListener(listener)
    this.popup = popup
    event = LightweightWindowEvent(popup)

    skipCaretEvent = true
    editor.addCaretListener(this)

    dialogComponent = editor.rootPane.parent
    dialogComponent?.addComponentListener(this)

    if (async) {
      SwingUtilities.invokeLater { show() }
    }
    else {
      show()
    }
  }

  private val popupLocation: Point
    get() {
      val location: Point = try {
        val view = editor.modelToView2D(caretPosition)
        Point(view.maxX.toInt(), view.maxY.toInt())
      }
      catch (ignore: BadLocationException) {
        editor.caret.magicCaretPosition
      }
      SwingUtilities.convertPointToScreen(location, editor)
      location.x -= getXOffset() + JBUIScale.scale(2)
      location.y += 2
      return location
    }
  val isValid: Boolean
    get() = popup!!.isVisible && popup!!.content.parent != null

  fun update() {
    skipCaretEvent = true
    popup?.apply {
      setLocation(popupLocation)
      pack(true, true)
    }
  }

  private fun show() {
    val currentList = list
    if (currentList != null) {
      if (currentList.model.size > 0) {
        currentList.selectedIndex = 0
      }
      else {
        currentList.clearSelection()
      }
    }
    popup?.showInScreenCoordinates(editor, popupLocation)
  }

  fun hide() {
    editor.removeCaretListener(this)
    if (dialogComponent != null) {
      dialogComponent?.removeComponentListener(this)
      dialogComponent = null
    }
    if (popup != null) {
      popup?.cancel()
      popup = null
    }
  }

  override fun caretUpdate(e: CaretEvent) {
    if (skipCaretEvent) {
      skipCaretEvent = false
    }
    else {
      hide()
      event?.let { listener.onClosed(it) }
    }
  }

  override fun componentMoved(e: ComponentEvent) {
    if (popup != null && isValid) {
      update()
    }
  }

  override fun componentResized(e: ComponentEvent) {
    componentMoved(e)
  }

  private fun getXOffset(): Int {
    return JBUIScale.scale(UIUtil.getListCellHPadding())
  }
}