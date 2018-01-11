/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */


package com.intellij.ui.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.DialogWrapperButtonLayout.Companion.EXTRA_WIDTH_KEY
import com.intellij.ui.components.JBOptionButton.PROP_OPTIONS
import com.intellij.ui.components.JBOptionButton.PROP_OPTION_TOOLTIP
import com.intellij.util.ui.AbstractLayoutManager
import com.intellij.util.ui.JBUI
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager
import java.awt.Rectangle
import java.awt.event.*
import java.beans.PropertyChangeListener
import javax.swing.AbstractButton.MNEMONIC_CHANGED_PROPERTY
import javax.swing.AbstractButton.TEXT_CHANGED_PROPERTY
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JComponent.TOOL_TIP_TEXT_KEY
import javax.swing.SwingUtilities
import javax.swing.SwingUtilities.replaceUIActionMap
import javax.swing.SwingUtilities.replaceUIInputMap
import javax.swing.event.ChangeListener

open class BasicOptionButtonUI : OptionButtonUI() {
  private var _optionButton: JBOptionButton? = null
  private var _mainButton: JButton? = null
  private var _arrowButton: JButton? = null
  protected val optionButton: JBOptionButton get() = _optionButton!!
  protected val mainButton: JButton get() = _mainButton!!
  protected val arrowButton: JButton get() = _arrowButton!!

  protected var propertyChangeListener: PropertyChangeListener? = null
  protected var changeListener: ChangeListener? = null
  protected var focusListener: FocusListener? = null
  protected var arrowButtonActionListener: ActionListener? = null
  protected var arrowButtonMouseListener: MouseListener? = null

  protected val isSimpleButton get() = optionButton.isSimpleButton

  override fun installUI(c: JComponent) {
    _optionButton = c as JBOptionButton

    installButtons()
    installListeners()
    installKeyboardActions()
  }

  override fun uninstallUI(c: JComponent) {
    uninstallKeyboardActions()
    uninstallListeners()
    uninstallButtons()

    _optionButton = null
  }

  override fun getPreferredSize(c: JComponent) = Dimension(mainButton.preferredSize.width + arrowButton.preferredSize.width,
                                                           maxOf(mainButton.preferredSize.height, arrowButton.preferredSize.height))

  protected open fun installButtons() {
    _mainButton = createMainButton()
    optionButton.add(mainButton)
    configureMainButton()

    _arrowButton = createArrowButton()
    optionButton.add(arrowButton)
    configureArrowButton()

    configureOptionButton()
  }

  protected open fun uninstallButtons() {
    unconfigureMainButton()
    unconfigureArrowButton()
    unconfigureOptionButton()

    _mainButton = null
    _arrowButton = null
  }

  protected open fun configureOptionButton() {
    optionButton.layout = createLayoutManager()
    updateExtraWidth()
  }

  protected open fun unconfigureOptionButton() {
    optionButton.layout = null
    optionButton.putClientProperty(EXTRA_WIDTH_KEY, null)
    optionButton.removeAll()
  }

  protected open fun createMainButton(): JButton = MainButton()

  protected open fun configureMainButton() {
    mainButton.isFocusable = false
  }

  protected open fun unconfigureMainButton() {
  }

  protected open fun createArrowButton(): JButton = ArrowButton().apply { icon = AllIcons.General.ArrowDown }

  protected open fun configureArrowButton() {
    arrowButton.isFocusable = false
    arrowButton.preferredSize = arrowButtonPreferredSize
    arrowButton.isVisible = !isSimpleButton

    arrowButtonActionListener = createArrowButtonActionListener()?.apply(arrowButton::addActionListener)
    arrowButtonMouseListener = createArrowButtonMouseListener()?.apply(arrowButton::addMouseListener)
  }

  protected open fun unconfigureArrowButton() {
    arrowButton.removeActionListener(arrowButtonActionListener)
    arrowButton.removeMouseListener(arrowButtonMouseListener)
    arrowButtonActionListener = null
    arrowButtonMouseListener = null
  }

  protected open val arrowButtonPreferredSize: Dimension get() = JBUI.size(16)

  protected open fun createLayoutManager(): LayoutManager = OptionButtonLayout()

  protected open fun installListeners() {
    propertyChangeListener = createPropertyChangeListener()?.apply(optionButton::addPropertyChangeListener)
    changeListener = createChangeListener()?.apply(optionButton::addChangeListener)
    focusListener = createFocusListener()?.apply(optionButton::addFocusListener)
  }

  protected open fun uninstallListeners() {
    optionButton.removePropertyChangeListener(propertyChangeListener)
    optionButton.removeChangeListener(changeListener)
    optionButton.removeFocusListener(focusListener)
    propertyChangeListener = null
    changeListener = null
    focusListener = null
  }

  protected open fun createPropertyChangeListener(): PropertyChangeListener? = PropertyChangeListener {
    when (it.propertyName) {
      "action" -> mainButton.action = optionButton.action
      TEXT_CHANGED_PROPERTY -> mainButton.text = optionButton.text
      MNEMONIC_CHANGED_PROPERTY -> mainButton.mnemonic = optionButton.mnemonic
      TOOL_TIP_TEXT_KEY, PROP_OPTION_TOOLTIP -> updateTooltip()
      PROP_OPTIONS -> {
        updateExtraWidth()
        updateTooltip()
        arrowButton.isVisible = !isSimpleButton
      }
    }
  }

  protected open fun createChangeListener(): ChangeListener? = ChangeListener {
    arrowButton.isEnabled = optionButton.isEnabled // mainButton is updated from corresponding Action instance
  }

  protected open fun createFocusListener(): FocusListener? = object : FocusAdapter() {
    override fun focusLost(e: FocusEvent?) {
      repaint()
    }

    override fun focusGained(e: FocusEvent?) {
      repaint()
    }

    private fun repaint() {
      mainButton.repaint()
      arrowButton.repaint()
    }
  }

  protected open fun createArrowButtonActionListener(): ActionListener? = ActionListener { optionButton.togglePopup() }

  protected open fun createArrowButtonMouseListener(): MouseListener? = object : MouseAdapter() {
    override fun mousePressed(e: MouseEvent) {
      if (SwingUtilities.isLeftMouseButton(e)) {
        e.consume()
        arrowButton.doClick()
      }
    }
  }

  protected open fun installKeyboardActions() {
    replaceUIActionMap(optionButton, mainButton.actionMap)
    replaceUIInputMap(optionButton, JComponent.WHEN_FOCUSED, mainButton.inputMap)
  }

  protected open fun uninstallKeyboardActions() {
    replaceUIActionMap(optionButton, null)
    replaceUIInputMap(optionButton, JComponent.WHEN_FOCUSED, null)
  }

  private fun updateExtraWidth() {
    optionButton.putClientProperty(EXTRA_WIDTH_KEY, if (!isSimpleButton) arrowButton.preferredSize.width else null)
  }

  private fun updateTooltip() {
    val toolTip = if (!isSimpleButton) optionButton.optionTooltipText else optionButton.toolTipText

    mainButton.toolTipText = toolTip
    arrowButton.toolTipText = toolTip
  }

  open inner class BaseButton : JButton() {
    override fun hasFocus() = optionButton.hasFocus()
    override fun isDefaultButton() = optionButton.isDefaultButton
  }

  open inner class MainButton : BaseButton()

  open inner class ArrowButton : BaseButton()

  open inner class OptionButtonLayout : AbstractLayoutManager() {
    override fun layoutContainer(parent: Container) {
      val mainButtonWidth = optionButton.width - if (arrowButton.isVisible) arrowButton.preferredSize.width else 0

      mainButton.bounds = Rectangle(0, 0, mainButtonWidth, optionButton.height)
      arrowButton.bounds = Rectangle(mainButtonWidth, 0, arrowButton.preferredSize.width, optionButton.height)
    }

    override fun preferredLayoutSize(parent: Container) = parent.preferredSize
    override fun minimumLayoutSize(parent: Container) = parent.minimumSize
  }

  companion object {
    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun createUI(c: JComponent) = BasicOptionButtonUI()
  }
}