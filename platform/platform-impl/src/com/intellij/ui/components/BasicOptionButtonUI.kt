// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


package com.intellij.ui.components

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.OptionAction
import com.intellij.openapi.ui.popup.JBPopupAdapter
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Condition
import com.intellij.ui.ScreenUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBOptionButton.PROP_OPTIONS
import com.intellij.ui.components.JBOptionButton.PROP_OPTION_TOOLTIP
import com.intellij.ui.popup.ActionPopupStep
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.PopupFactoryImpl.ActionGroupPopup.getActionItems
import com.intellij.ui.popup.list.PopupListElementRenderer
import com.intellij.util.ui.AbstractLayoutManager
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.scale
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeListener
import java.util.function.Supplier
import javax.swing.*
import javax.swing.AbstractButton.MNEMONIC_CHANGED_PROPERTY
import javax.swing.AbstractButton.TEXT_CHANGED_PROPERTY
import javax.swing.JComponent.TOOL_TIP_TEXT_KEY
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

  protected var popup: ListPopup? = null
  protected var showPopupAction: AnAction? = null
  protected var isPopupShowing: Boolean = false

  protected var propertyChangeListener: PropertyChangeListener? = null
  protected var changeListener: ChangeListener? = null
  protected var focusListener: FocusListener? = null
  protected var arrowButtonActionListener: ActionListener? = null
  protected var arrowButtonMouseListener: MouseListener? = null

  protected val isSimpleButton: Boolean get() = optionButton.isSimpleButton

  override fun installUI(c: JComponent) {
    _optionButton = c as JBOptionButton

    installPopup()
    installButtons()
    installListeners()
    installKeyboardActions()
  }

  override fun uninstallUI(c: JComponent) {
    uninstallKeyboardActions()
    uninstallListeners()
    uninstallButtons()
    uninstallPopup()

    _optionButton = null
  }

  override fun getPreferredSize(c: JComponent): Dimension = Dimension(mainButton.preferredSize.width + arrowButton.preferredSize.width,
                                                                      maxOf(mainButton.preferredSize.height, arrowButton.preferredSize.height))

  protected open fun installPopup() {
    showPopupAction = DumbAwareAction.create { _ -> showPopup() }
    showPopupAction?.registerCustomShortcutSet(CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)), optionButton)
  }

  protected open fun uninstallPopup() {
    showPopupAction?.unregisterCustomShortcutSet(optionButton)
    showPopupAction = null

    popup?.let(Disposable::dispose)
    popup = null
  }

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
  }

  protected open fun unconfigureOptionButton() {
    optionButton.layout = null
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
        closePopup()
        updateTooltip()
        updateOptions()
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

  protected open fun createArrowButtonActionListener(): ActionListener? = ActionListener { togglePopup() }

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

  override fun showPopup(toSelect: Action?, ensureSelection: Boolean) {
    if (!isSimpleButton) {
      isPopupShowing = true
      popup = createPopup(toSelect, ensureSelection).apply {
        // use invokeLater() to update flag "after" popup is auto-closed - to ensure correct togglePopup() behaviour on arrow button press
        setFinalRunnable { getApplication().invokeLater { isPopupShowing = false } }
        addListener(object : JBPopupAdapter() {
          override fun beforeShown(event: LightweightWindowEvent) {
            val popup = event.asPopup()
            val screen = ScreenUtil.getScreenRectangle(optionButton.locationOnScreen)
            val above = screen.height < popup.size.height + showPopupBelowLocation.screenPoint.y

            if (above) {
              val point = Point(showPopupAboveLocation.screenPoint)
              point.translate(0, -popup.size.height)
              popup.setLocation(point)
            }
          }

          override fun onClosed(event: LightweightWindowEvent) {
            // final runnable is not called when some action is invoked - so we handle this case here separately
            if (event.isOk) {
              isPopupShowing = false
            }
          }
        })
        show(showPopupBelowLocation)
      }
    }
  }

  override fun closePopup() {
    popup?.cancel()
  }

  override fun togglePopup() {
    if (isPopupShowing) {
      closePopup()
    }
    else {
      showPopup()
    }
  }

  protected open val showPopupXOffset: Int get() = 0
  protected open val showPopupBelowLocation: RelativePoint get() = RelativePoint(optionButton, Point(showPopupXOffset, optionButton.height + scale(6)))
  protected open val showPopupAboveLocation: RelativePoint get() = RelativePoint(optionButton, Point(showPopupXOffset, -scale(6)))

  protected open fun createPopup(toSelect: Action?, ensureSelection: Boolean): ListPopup {
    val (actionGroup, mapping) = createActionMapping()
    val dataContext = createActionDataContext()
    val actionItems = getActionItems(actionGroup, dataContext, false, false, true, true, ActionPlaces.UNKNOWN)
    val defaultSelection = if (toSelect != null) Condition<AnAction> { mapping[it] == toSelect } else null

    return OptionButtonPopup(OptionButtonPopupStep(actionItems, defaultSelection), dataContext, toSelect != null || ensureSelection)
  }

  protected open fun createActionDataContext(): DataContext = DataManager.getInstance().getDataContext(optionButton)

  protected open fun createActionMapping(): Pair<ActionGroup, Map<AnAction, Action>> {
    val mapping = optionButton.options?.associateBy(this@BasicOptionButtonUI::createAnAction) ?: emptyMap()
    val actionGroup = DefaultActionGroup().apply {
      mapping.keys.forEachIndexed { index, it ->
        if (index > 0) addSeparator()
        add(it)
      }
    }

    return Pair(actionGroup, mapping)
  }

  protected open fun createAnAction(action: Action): AnAction = action.getValue(OptionAction.AN_ACTION) as? AnAction ?: ActionDelegate(action)

  private fun updateTooltip() {
    val toolTip = if (!isSimpleButton) optionButton.optionTooltipText else optionButton.toolTipText

    mainButton.toolTipText = toolTip
    arrowButton.toolTipText = toolTip
  }

  protected open fun updateOptions() {
    arrowButton.isVisible = !isSimpleButton
  }

  open inner class BaseButton : JButton() {
    override fun hasFocus(): Boolean = optionButton.hasFocus()
    override fun isDefaultButton(): Boolean = optionButton.isDefaultButton

    override fun paint(g: Graphics): Unit = if (isSimpleButton) super.paint(g) else cloneAndPaint(g) { paintNotSimple(it) }
    open fun paintNotSimple(g: Graphics2D): Unit = super.paint(g)

    override fun paintBorder(g: Graphics): Unit = if (isSimpleButton) super.paintBorder(g) else cloneAndPaint(g) { paintBorderNotSimple(it) }
    open fun paintBorderNotSimple(g: Graphics2D): Unit = super.paintBorder(g)
  }

  open inner class MainButton : BaseButton()

  open inner class ArrowButton : BaseButton()

  open inner class OptionButtonLayout : AbstractLayoutManager() {
    override fun layoutContainer(parent: Container) {
      val mainButtonWidth = optionButton.width - if (arrowButton.isVisible) arrowButton.preferredSize.width else 0

      mainButton.bounds = Rectangle(0, 0, mainButtonWidth, optionButton.height)
      arrowButton.bounds = Rectangle(mainButtonWidth, 0, arrowButton.preferredSize.width, optionButton.height)
    }

    override fun preferredLayoutSize(parent: Container): Dimension = parent.preferredSize
    override fun minimumLayoutSize(parent: Container): Dimension = parent.minimumSize
  }

  open inner class OptionButtonPopup(step: ActionPopupStep, dataContext: DataContext, private val ensureSelection: Boolean)
    : PopupFactoryImpl.ActionGroupPopup(null, step, null, dataContext, ActionPlaces.UNKNOWN, -1) {
    init {
      list.background = background
    }

    override fun afterShow() {
      if (ensureSelection) super.afterShow()
    }

    protected val background: Color? get() = mainButton.background

    override fun createContent(): JComponent = super.createContent().also {
      list.clearSelection() // prevents first action selection if all actions are disabled
      list.border = JBUI.Borders.empty(2, 0)
    }

    override fun getListElementRenderer(): PopupListElementRenderer<Any> = object : PopupListElementRenderer<Any>(this) {
      override fun getBackground() = this@OptionButtonPopup.background
      override fun createSeparator() = super.createSeparator().apply { border = JBUI.Borders.empty(2, 6) }
      override fun getDefaultItemComponentBorder() = JBUI.Borders.empty(6, 8)
    }
  }

  open inner class OptionButtonPopupStep(actions: List<PopupFactoryImpl.ActionItem>, private val defaultSelection: Condition<AnAction>?)
    : ActionPopupStep(actions, null,
                      Supplier<DataContext> { DataManager.getInstance().getDataContext(optionButton) },
                      null, true, defaultSelection, false, true) {
    // if there is no default selection condition - -1 should be returned, this way first enabled action should be selected by
    // OptionButtonPopup.afterShow() (if corresponding ensureSelection parameter is true)
    override fun getDefaultOptionIndex(): Int = defaultSelection?.let { super.getDefaultOptionIndex() } ?: -1
    override fun isSpeedSearchEnabled(): Boolean = false
  }

  open inner class ActionDelegate(val action: Action) : DumbAwareAction() {
    init {
      isEnabledInModalContext = true
      templatePresentation.text = (action.getValue(Action.NAME) as? String).orEmpty()
    }

    override fun update(event: AnActionEvent) {
      event.presentation.isEnabled = action.isEnabled
    }

    override fun actionPerformed(event: AnActionEvent) {
      action.actionPerformed(ActionEvent(optionButton, ActionEvent.ACTION_PERFORMED, null))
    }
  }

  companion object {
    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun createUI(c: JComponent): BasicOptionButtonUI = BasicOptionButtonUI()

    fun paintBackground(g: Graphics, c: JComponent) {
      g.color = c.background
      g.fillRect(0, 0, c.width, c.height)
    }

    fun cloneAndPaint(g: Graphics, block: (Graphics2D) -> Unit) {
      val g2 = g.create() as Graphics2D
      try {
        block(g2)
      }
      finally {
        g2.dispose()
      }
    }
  }
}