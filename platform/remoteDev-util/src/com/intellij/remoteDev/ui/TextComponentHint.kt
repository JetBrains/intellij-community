package com.intellij.remoteDev.ui

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.JLabel
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.Document
import javax.swing.text.JTextComponent

@ApiStatus.Experimental
class TextComponentHint(val component: JTextComponent, @Nls text: String, var state: State = State.FOCUS_LOST) : JLabel(text) {
  enum class State {
    ALWAYS, FOCUS_GAINED, FOCUS_LOST
  }

  private val document: Document = component.document
  private var showHintOnce = false
  private var focusLost = 0

  private val documentListener = object : DocumentListener {
    override fun insertUpdate(e: DocumentEvent) {
      checkForHint()
    }

    override fun removeUpdate(e: DocumentEvent) {
      checkForHint()
    }

    override fun changedUpdate(e: DocumentEvent) {}
  }

  private val focusListener = object : FocusListener {
    override fun focusGained(e: FocusEvent) {
      checkForHint()
    }

    override fun focusLost(e: FocusEvent) {
      focusLost++
      checkForHint()
    }
  }

  init {
    font = component.font
    foreground = component.foreground
    border = EmptyBorder(component.insets)
    horizontalAlignment = LEADING

    setAlpha(0.5f)
    setStyle(Font.ITALIC)

    component.addFocusListener(focusListener)
    document.addDocumentListener(documentListener)

    component.layout = BorderLayout()
    component.add(this)
    checkForHint()
  }

  private fun setAlpha(alpha: Float) {
    setAlpha((alpha * 255).toInt())
  }

  private fun setAlpha(value: Int) {
    var alpha = value
    alpha = if (alpha > 255) 255 else if (alpha < 0) 0 else alpha
    val foreground = foreground
    val red = foreground.red
    val green = foreground.green
    val blue = foreground.blue
    val withAlpha = Color(red, green, blue, alpha)
    super.setForeground(withAlpha)
  }

  private fun setStyle(style: Int) {
    font = font.deriveFont(style)
  }

  fun getShowHintOnce(): Boolean {
    return showHintOnce
  }

  fun setShowHintOnce(showHintOnce: Boolean) {
    this.showHintOnce = showHintOnce
  }

  private fun checkForHint() {
    if (document.length > 0) {
      isVisible = false
      return
    }

    if (showHintOnce && focusLost > 0) {
      isVisible = false
      return
    }

    isVisible = if (component.hasFocus()) {
      state == State.ALWAYS || state == State.FOCUS_GAINED
    }
    else {
      state == State.ALWAYS || state == State.FOCUS_LOST
    }
  }
}