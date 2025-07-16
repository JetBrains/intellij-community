// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.messages

import com.intellij.BundleBase
import com.intellij.diagnostic.LoadingState
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.ExitActionType
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.*
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.mac.touchbar.Touchbar
import com.intellij.util.ui.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import javax.accessibility.AccessibleContext
import javax.swing.*
import javax.swing.border.Border
import javax.swing.plaf.basic.BasicHTML
import javax.swing.text.View
import kotlin.math.min

/**
 * **Never use directly**, call [com.intellij.openapi.ui.MessageDialogBuilder] or [Messages] instead.
 * Supersedes one unfortunate [com.intellij.ui.messages.NativeMacMessageManager].
 */
@Service
@ApiStatus.Internal
internal class AlertMessagesManager {

  companion object {

    @JvmStatic
    fun getInstanceIfPossible(): AlertMessagesManager? {
      return if (LoadingState.COMPONENTS_LOADED.isOccurred
                 && RegistryManager.getInstance().`is`("ide.message.dialogs.as.swing.alert"))
        ApplicationManager.getApplication().service<AlertMessagesManager>()
      else
        null
    }
  }

  fun showMessageDialog(project: Project?,
                        parentComponent: Component?,
                        @NlsContexts.DialogMessage message: String?,
                        @NlsContexts.DialogTitle title: String?,
                        options: Array<String>,
                        defaultOptionIndex: Int,
                        focusedOptionIndex: Int,
                        icon: Icon?,
                        doNotAskOption: DoNotAskOption?,
                        helpId: String?,
                        invocationPlace: String?,
                        exitActionTypes: Array<ExitActionType>): Int {
    val dialog = AlertDialog(project, parentComponent, message, title, options, defaultOptionIndex, focusedOptionIndex, getIcon(icon),
                             doNotAskOption, helpId, invocationPlace, exitActionTypes)
    AppIcon.getInstance().requestAttention(project, true)
    dialog.show()
    return dialog.exitCode
  }

  private fun getIcon(icon: Icon?): Icon {
    if (icon == UIUtil.getInformationIcon() || icon == null) {
      return AllIcons.General.InformationDialog
    }
    if (icon == UIUtil.getQuestionIcon()) {
      return AllIcons.General.QuestionDialog
    }
    if (icon == UIUtil.getWarningIcon()) {
      return AllIcons.General.WarningDialog
    }
    if (icon == UIUtil.getErrorIcon()) {
      return AllIcons.General.ErrorDialog
    }
    return icon
  }
}

private const val PARENT_WIDTH_KEY = "parent.width"

@ApiStatus.Internal
class AlertDialog(project: Project?,
                  parentComponent: Component?,
                  @NlsContexts.DialogMessage val myMessage: String?,
                  @NlsContexts.DialogTitle val myTitle: String?,
                  val myOptions: Array<String>,
                  val myDefaultOptionIndex: Int,
                  val myFocusedOptionIndex: Int,
                  icon: Icon,
                  doNotAskOption: DoNotAskOption?,
                  val myHelpId: String?,
                  invocationPlace: String? = null,
                  val exitActionTypes: Array<ExitActionType> = emptyArray()) : DialogWrapper(project, parentComponent, false, IdeModalityType.IDE, false) {

  private val myIsTitleComponent = SystemInfoRt.isMac || !Registry.`is`("ide.message.dialogs.as.swing.alert.show.title.bar", false)

  private val myRootLayout = RootLayout()
  private val myIconComponent = JLabel(icon)
  private var myMessageComponent: JComponent? = null
  private val mySouthPanel = JPanel(BorderLayout())
  private val myButtonsPanel = JPanel()
  private val myCloseButton: JComponent?
  private val myButtons = ArrayList<JButton>()
  private var myHelpButton: JButton? = null
  private var myInitSize: Dimension? = null

  init {
    title = myTitle
    setDoNotAskOption(doNotAskOption)
    setInvocationPlace(invocationPlace)

    if (myIsTitleComponent && !SystemInfoRt.isMac) {
      myCloseButton = object : InplaceButton(IconButton(null, AllIcons.Windows.CloseActive, null, null), {
        doCancelAction()
      }) {
        override fun paintHover(g: Graphics) {
          paintHover(g, false)
        }
      }
      myCloseButton.preferredSize = JBDimension(22, 22)
    }
    else {
      myCloseButton = null
    }

    if (SystemInfoRt.isMac) {
      setInitialLocationCallback {
        val rootPane: JRootPane? = SwingUtilities.getRootPane(window.parent) ?: SwingUtilities.getRootPane(window.owner)
        if (rootPane == null || !rootPane.isShowing) {
          return@setInitialLocationCallback null
        }
        val location = rootPane.locationOnScreen
        Point(location.x + (rootPane.width - window.width) / 2, (location.y + rootPane.height * 0.25).toInt())
      }
    }

    init()

    if (myHelpButton != null) {
      val helpButton = myHelpButton!!
      helpButton.parent.remove(helpButton)
      myRootLayout.addAdditionalComponent(helpButton)
    }

    if (myCloseButton != null) {
      myRootLayout.addAdditionalComponent(myCloseButton)
    }

    if (myIsTitleComponent) {
      setUndecorated(true)
      rootPane.windowDecorationStyle = JRootPane.NONE
      rootPane.border = PopupBorder.Factory.create(true, true)

      object : MouseDragHelper<JComponent>(myDisposable, contentPane as JComponent) {
        var myLocation: Point? = null

        override fun canStartDragging(dragComponent: JComponent, dragComponentPoint: Point): Boolean {
          val target = dragComponent.findComponentAt(dragComponentPoint)
          return target == null || target == dragComponent || target is JPanel
        }

        override fun processDrag(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point) {
          if (myLocation == null) {
            myLocation = window.location
          }
          window.location = Point(myLocation!!.x + dragToScreenPoint.x - startScreenPoint.x,
                                  myLocation!!.y + dragToScreenPoint.y - startScreenPoint.y)
        }

        override fun processDragCancel() {
          myLocation = null
        }

        override fun processDragFinish(event: MouseEvent, willDragOutStart: Boolean) {
          myLocation = null
        }

        override fun processDragOutFinish(event: MouseEvent) {
          myLocation = null
        }

        override fun processDragOutCancel() {
          myLocation = null
        }

        override fun processDragOut(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point, justStarted: Boolean) {
          super.processDragOut(event, dragToScreenPoint, startScreenPoint, justStarted)
          myLocation = null
        }
      }.start()
    }

    WindowRoundedCornersManager.configure(this)
  }

  override fun setSizeDuringPack() = false

  override fun sortActionsOnMac(actions: MutableList<Action>) {
    actions.reverse()
  }

  override fun beforeShowCallback() {
    if (SystemInfoRt.isMac) {
      val initSize = myInitSize!!
      if (!size.equals(initSize)) {
        setSize(initSize.width, initSize.height)
        val location = initialLocation
        if (location != null) {
          setLocation(location.x, location.y)
        }
      }
    }
  }

  override fun getInitialSize(): Dimension {
    if (myInitSize == null) {
      myInitSize = calculateInitialSize()
    }
    return myInitSize!!
  }

  private fun calculateInitialSize(): Dimension {
    val buttonsWidth = myButtonsPanel.preferredSize.width

    if (buttonsWidth > JBUI.scale(348)) {
      configureMessageWidth(min(buttonsWidth, JBUI.scale(450)))
      return preferredSize
    }

    val width = JBUI.scale(if (buttonsWidth <= JBUI.scale(278) && StringUtil.length(myMessage) <= 130) 370 else 440)

    configureMessageWidth(width - myRootLayout.getWidthWithoutMessageComponent())

    return Dimension(width, preferredSize.height)
  }

  private fun configureMessageWidth(width: Int) {
    if (myMessageComponent == null) {
      return
    }
    val scrollPane = ComponentUtil.getScrollPane(myMessageComponent)
    if (scrollPane == null) {
      myMessageComponent!!.putClientProperty(PARENT_WIDTH_KEY, width)
    }
    else {
      scrollPane.preferredSize = Dimension(width, scrollPane.preferredSize.height)
    }
  }

  override fun createContentPaneBorder(): Border {
    val insets = JButton().insets
    return JBUI.Borders.empty(if (myIsTitleComponent) 20 else 14, 20, 20 - insets.bottom, 20 - insets.right)
  }

  override fun createRootLayout(): LayoutManager = myRootLayout

  private inner class RootLayout : BorderLayout() {
    private var myParent: Container? = null

    fun getWidthWithoutMessageComponent(): Int {
      val insets = myParent!!.insets
      val layout = myIconComponent.parent.parent.layout as BorderLayout
      return insets.left + myIconComponent.preferredSize.width + layout.hgap + insets.right
    }

    fun addAdditionalComponent(component: Component) {
      myParent!!.add(component, 0)
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun addLayoutComponent(name: String?, comp: Component) {
      if (myParent == null) {
        myParent = comp.parent
      }
      if (comp != myCloseButton && comp != myHelpButton) {
        super.addLayoutComponent(name, comp)
      }
    }

    override fun layoutContainer(target: Container) {
      super.layoutContainer(target)

      if (myCloseButton != null) {
        val offset = JBUI.scale(24)
        val size = myCloseButton.preferredSize
        myCloseButton.setBounds(target.width - offset, offset - size.height, size.width, size.height)
      }

      if (myHelpButton != null) {
        val helpButton = myHelpButton!!
        val firstButton = myButtons[0]

        val iconPoint = SwingUtilities.convertPoint(myIconComponent, 0, 0, target)

        val iconSize = myIconComponent.preferredSize
        val helpSize = helpButton.preferredSize
        val buttonSize = firstButton.preferredSize

        helpButton.setBounds(iconPoint.x + (iconSize.width - helpSize.width) / 2,
                             target.height - target.insets.bottom - (buttonSize.height + helpSize.height) / 2,
                             helpSize.width, helpSize.height)
      }
    }
  }

  override fun createCenterPanel(): JComponent {
    val dialogPanel = JPanel(BorderLayout(JBUI.scale(12), 0))

    val iconPanel = JPanel(BorderLayout())
    iconPanel.add(myIconComponent, BorderLayout.NORTH)
    dialogPanel.add(iconPanel, BorderLayout.WEST)

    val textPanel = JPanel(BorderLayout(0, JBUI.scale(8)))
    dialogPanel.add(textPanel)

    val singleSelectionHandler = SingleTextSelectionHandler()

    if (myIsTitleComponent && !StringUtil.isEmpty(myTitle)) {
      val title = UIUtil.replaceMnemonicAmpersand(myTitle!!).replace(BundleBase.MNEMONIC_STRING, "")
      val titleComponent = createTextComponent(object : JEditorPane() {
        override fun getPreferredSize(): Dimension {
          val size = super.getPreferredSize()
          return Dimension(min(size.width, JBUI.scale(450)), size.height)
        }
      }, StringUtil.trimLog(title, 100))
      titleComponent.font = JBFont.h4()
      textPanel.add(wrapWithMinWidth(titleComponent), BorderLayout.NORTH)
      singleSelectionHandler.add(titleComponent, false)
    }

    if (!StringUtil.isEmpty(myMessage)) {
      val messageComponent = createTextComponent(object : JEditorPane() {
        override fun getPreferredSize(): Dimension {
          val parentWidth = getClientProperty(PARENT_WIDTH_KEY)
          if (parentWidth is Int) {
            val view = getUI().getRootView(this)
            view.setSize(parentWidth.toFloat(), Int.MAX_VALUE.toFloat())
            return Dimension(parentWidth, view.getPreferredSpan(View.Y_AXIS).toInt())
          }
          return super.getPreferredSize()
        }
      }, myMessage!!.replace("(\r\n|\n)".toRegex(), "<br/>"))

      messageComponent.font = JBFont.regular()
      myMessageComponent = messageComponent
      singleSelectionHandler.add(messageComponent, false)

      textPanel.add(wrapWithMinWidth(wrapToScrollPaneIfNeeded(messageComponent)))
    }

    textPanel.add(mySouthPanel, BorderLayout.SOUTH)

    createSouthPanel()

    val buttonInsets = if (myButtons.size > 0) myButtons[0].insets else Insets(0, 0, 0, 0)

    if (myCheckBoxDoNotShowDialog == null || !myCheckBoxDoNotShowDialog.isVisible) {
      // vertical gap 22 between text message and visual part of buttons
      myButtonsPanel.border = JBUI.Borders.emptyTop(14 - JBUI.unscale(buttonInsets.top)) // +8 from textPanel layout vGap
    }
    else {
      myCheckBoxDoNotShowDialog.font = JBFont.regular()
      val wrapper = Wrapper(myCheckBoxDoNotShowDialog)
      val checkBoxLeftOffset = myCheckBoxDoNotShowDialog.insets.left
      // vertical gap 12 between text message and check box
      wrapper.border = JBUI.Borders.emptyTop(4) // +8 from textPanel layout vGap
      for (child in UIUtil.uiChildren(textPanel)) {
        if (child != mySouthPanel) {
          (child as JComponent).border = JBUI.Borders.emptyLeft(JBUI.unscale(checkBoxLeftOffset))
        }
      }
      (dialogPanel.layout as BorderLayout).hgap -= checkBoxLeftOffset
      // vertical gap 22 between check box and visual part of buttons
      (mySouthPanel.layout as BorderLayout).vgap = JBUI.scale(22 - JBUI.unscale(buttonInsets.top))
      mySouthPanel.add(wrapper, BorderLayout.NORTH)
    }

    myButtonsPanel.layout = HorizontalLayout(JBUI.scale(12 - JBUI.unscale(buttonInsets.left - buttonInsets.right)))

    for (button in myButtons) {
      button.parent.remove(button)
      myButtonsPanel.add(button, HorizontalLayout.RIGHT)
    }

    if (SystemInfoRt.isMac) {
      val buttonMap = buttonMap
      buttonMap.clear()
      myButtons.forEach { buttonMap[it.action] = it }

      Touchbar.setButtonActions(myButtonsPanel, null, myButtons, myButtons.find { b -> b.action.getValue(DEFAULT_ACTION) != null })
    }

    mySouthPanel.add(myButtonsPanel)

    singleSelectionHandler.start()

    return dialogPanel
  }

  private fun wrapToScrollPaneIfNeeded(messageComponent: JEditorPane): JComponent {
    val width450 = JBUI.scale(450)
    val size2D = messageComponent.font.size2D
    val maximumHeight = (size2D * 9.45).toInt()
    val preferred: Dimension = messageComponent.preferredSize

    if (preferred.height < maximumHeight) {
      val columnLength = (1.2 * width450 / size2D).toInt()
      if (messageComponent.document.length < columnLength * 8) {
        return messageComponent
      }
    }

    val scrollPane = ScrollPaneFactory.createScrollPane(messageComponent, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED)
    scrollPane.border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
    scrollPane.isOpaque = false
    scrollPane.viewport.isOpaque = false

    val barWidth = UIUtil.getScrollBarWidth()
    scrollPane.preferredSize = Dimension(preferred.width.coerceAtMost(width450) + barWidth, maximumHeight + barWidth)

    return scrollPane
  }

  private fun wrapWithMinWidth(scrollPane: JComponent) = object : Wrapper(scrollPane) {
    override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
      super.setBounds(x, y, min(width, JBUI.scale(450)), height)
    }
  }

  private fun createTextComponent(component: JEditorPane, message: @Nls String?): JEditorPane {
    component.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, java.lang.Boolean.TRUE)
    component.contentType = "text/html"
    component.isEditable = false
    component.isOpaque = false
    component.border = null

    val kit = HTMLEditorKitBuilder().withWordWrapViewFactory().build()
    kit.styleSheet.addRule("a {color: " + ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.Foreground.ENABLED) + "}")
    component.editorKit = kit
    component.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)

    if (BasicHTML.isHTMLString(message)) {
      component.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY,
                                  StringUtil.unescapeXmlEntities(StringUtil.stripHtml(message!!, " ")))
    }

    component.text = message

    component.isEditable = false
    if (component.caret != null) {
      component.caretPosition = 0
    }

    return component
  }

  override fun createActions(): Array<Action> {
    val actions: MutableList<Action> = ArrayList()
    for (i in myOptions.indices) {
      val option = myOptions[i]
      val exitActionType = if (exitActionTypes.size > i) exitActionTypes[i] else ExitActionType.UNDEFINED
      val action: Action = object : AbstractAction(UIUtil.replaceMnemonicAmpersand(option)) {
        override fun actionPerformed(e: ActionEvent) {
          close(i, true, exitActionType)
        }
      }
      if (i == myDefaultOptionIndex) {
        action.putValue(DEFAULT_ACTION, java.lang.Boolean.TRUE)
      }
      if (i == myFocusedOptionIndex) {
        action.putValue(FOCUSED_ACTION, java.lang.Boolean.TRUE)
      }
      UIUtil.assignMnemonic(option, action)
      actions.add(action)
    }

    if (helpId != null) {
      actions.add(helpAction)
    }
    return actions.toTypedArray()
  }

  override fun createJButtonForAction(action: Action): JButton {
    val button = super.createJButtonForAction(action)
    val size = button.preferredSize
    val insets = button.insets

    val width = JBUI.scale(72) + insets.left + insets.right
    if (size.width < width) {
      size.width = width
    }
    else {
      val diffWidth = JBUI.scale(20) - UIUtil.getButtonTextHorizontalOffset(button, size, null)
      if (diffWidth > 0) {
        size.width += 2 * diffWidth
      }
    }

    val height = JBUI.scale(24) + insets.top + insets.bottom
    if (size.height < height) {
      size.height = height
    }

    button.preferredSize = size
    myButtons.add(button)

    return button
  }

  override fun createDoNotAskCheckbox(): JComponent? = null

  override fun getPreferredFocusedComponent(): JComponent? {
    if (!SystemInfoRt.isMac) {
      return null
    }
    if (myPreferredFocusedComponent != null) {
      return myPreferredFocusedComponent
    }
    val size = myButtons.size
    if (size > 0) {
      val buttons = if (SystemInfoRt.isMac) ArrayList<JButton>(myButtons).also { it.reverse() } else myButtons
      val cancelButton = Messages.getCancelButton()

      for ((index, button) in buttons.withIndex()) {
        if (index != myDefaultOptionIndex && (size < 3 || cancelButton != button.text)) {
          return button
        }
      }
    }
    return null
  }

  override fun doCancelAction() = close(-1, false, ExitActionType.CANCEL)

  override fun createHelpButton(insets: Insets): JButton {
    val helpButton = super.createHelpButton(insets)
    myHelpButton = helpButton
    return helpButton
  }

  override fun getHelpId(): String? = myHelpId

  public override fun toBeShown(): Boolean = super.toBeShown()
}
