// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.openapi.wm.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.actions.ToolWindowMoveAction
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.SquareStripeButton.Companion.createMoveGroup
import com.intellij.toolWindow.ResizeStripeManager
import com.intellij.toolWindow.StripeButtonUi
import com.intellij.toolWindow.ToolWindowEventSource
import com.intellij.toolWindow.ToolWindowLeftToolbar
import com.intellij.toolWindow.ToolWindowToolbar
import com.intellij.ui.ColorUtil
import com.intellij.ui.ComponentUtil
import com.intellij.ui.MouseDragHelper
import com.intellij.ui.PopupHandler
import com.intellij.ui.RelativeFont
import com.intellij.ui.UIBundle
import com.intellij.ui.icons.HoledIcon
import com.intellij.ui.icons.IconReplacer
import com.intellij.ui.icons.loadIconCustomVersionOrScale
import com.intellij.ui.icons.toStrokeIcon
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.UIManager

@ApiStatus.Internal
abstract class AbstractSquareStripeButton(
  action: AnAction, presentation: Presentation,
  minimumSize: Supplier<Dimension>? = null
) :
  ActionButton(action, presentation, ActionPlaces.TOOLWINDOW_TOOLBAR_BAR, minimumSize ?: Supplier { JBUI.CurrentTheme.Toolbar.stripeToolbarButtonSize() }) {

  protected fun doInit(popupBuilder: () -> ActionGroup) {
    setLook(SquareStripeButtonLook(this))
    addMouseListener(object : PopupHandler() {
      override fun invokePopup(component: Component, x: Int, y: Int) {
        ResizeStripeManager.showPopup(popupBuilder.invoke(), component, x, y)
      }
    })
  }

  fun paintDraggingButton(g: Graphics, isLeft: Boolean) {
    val areaSize = size.also {
      JBInsets.removeFrom(it, insets)
      JBInsets.removeFrom(it, SquareStripeButtonLook.getIconPadding(isLeft))
    }

    val color = JBUI.CurrentTheme.ToolWindow.DragAndDrop.BUTTON_FLOATING_BACKGROUND
    val rect = Rectangle(areaSize)
    buttonLook.paintLookBackground(g, rect, color)
    icon.let {
      val x = (areaSize.width - it.iconWidth) / 2
      val y = (areaSize.height - it.iconHeight) / 2
      buttonLook.paintIcon(g, this, it, x, y)
    }

    buttonLook.paintLookBorder(g, rect, color)
  }
}

@ApiStatus.Internal
class SquareStripeButton(val toolWindow: ToolWindowImpl) :
  AbstractSquareStripeButton(SquareAnActionButton(toolWindow), createPresentation(toolWindow), null) {

  companion object {
    fun createMoveGroup(): ToolWindowMoveAction.Group = ToolWindowMoveAction.Group()
  }

  private var myShowName = false

  init {
    doInit { createPopupGroup(toolWindow) }
    MouseDragHelper.setComponentDraggable(this, true)
    setLook(SquareStripeButtonLook(this))
  }

  override fun paintButtonLook(g: Graphics) {
    val look = buttonLook

    if (look is SquareStripeButtonLookExtension) {
      look.paintButtonLook(g)
    } else {
      super.paintButtonLook(g)
    }
  }

  override fun updateUI() {
    super.updateUI()

    myPresentation.icon = scaleIcon((toolWindow.icon ?: AllIcons.Toolbar.Unknown) as ScalableIcon)
    myPresentation.isEnabledAndVisible = true
  }

  fun updatePresentation() {
    updateToolTipText()

    myPresentation.icon = scaleIcon((toolWindow.icon ?: AllIcons.Toolbar.Unknown) as ScalableIcon)
  }

  fun isHovered(): Boolean = myRollover

  fun isFocused(): Boolean = toolWindow.isActive

  fun resetDrop() {
    resetMouseState()
  }

  override fun updateToolTipText() {
    HelpTooltip()
      .setPlainTextTitle(toolWindow.stripeTitleProvider)
      .setLocation(getAlignment(toolWindow.anchor, toolWindow.isSplitMode))
      .setShortcut(ActionManager.getInstance().getKeyboardShortcut(ActivateToolWindowAction.Manager.getActionIdForToolWindow(toolWindow.id)))
      .setInitialDelay(0)
      .setHideDelay(0)
      .installOn(this)
    HelpTooltip.setMasterPopupOpenCondition(this) { !((parent as? AbstractDroppableStripe)?.isDroppingButton() ?: false) }

    setOrUpdateShowName(ResizeStripeManager.isShowNames())
  }

  override fun checkSkipPressForEvent(e: MouseEvent): Boolean = e.button != MouseEvent.BUTTON1

  private fun getAlignment(anchor: ToolWindowAnchor, splitMode: Boolean): HelpTooltip.Alignment {
    return when (anchor) {
      ToolWindowAnchor.RIGHT -> HelpTooltip.Alignment.LEFT
      ToolWindowAnchor.TOP -> HelpTooltip.Alignment.LEFT
      ToolWindowAnchor.LEFT -> HelpTooltip.Alignment.RIGHT
      ToolWindowAnchor.BOTTOM -> if (splitMode) HelpTooltip.Alignment.LEFT else HelpTooltip.Alignment.RIGHT
      else -> HelpTooltip.Alignment.RIGHT
    }
  }

  fun setOrUpdateShowName(value: Boolean) {
    if (myShowName != value) {
      myShowName = value

      setLook(if (value) SquareStripeButtonLookHorizontalText(this) else SquareStripeButtonLook(this))
      revalidate()
      repaint()
    }
  }

  override fun getPreferredSize(): Dimension {
    val size = super.getPreferredSize()
    val look = buttonLook

    return if (look is SquareStripeButtonLookExtension) look.getPreferredSize(size)
    else size
  }
}

@ApiStatus.Internal
abstract class SquareStripeButtonLookExtension(protected val button: SquareStripeButton): SquareStripeButtonLook(button) {

  abstract fun getPreferredSize(size: Dimension): Dimension

  protected val toolWindow: ToolWindowImpl
    get() = button.toolWindow

  fun paintButtonLook(g: Graphics) {
    paintBackground(g, button)
    paintIcon(g, button, button.icon)

    val color = if (button.popState == ActionButtonComponent.PUSHED) JBUI.CurrentTheme.ActionButton.pressedBorder()
    else JBUI.CurrentTheme.ActionButton.hoverBorder()

    if (color.alpha == 255) {
      paintBorder(g, button)
    }
  }

  protected fun getBackgroundColor(): Color {
    if (button.isFocused()) {
      return StripeButtonUi.SELECTED_BACKGROUND_COLOR
    }
    if (toolWindow.isVisible) {
      return JBUI.CurrentTheme.ActionButton.pressedBackground()
    }
    if (button.isHovered()) {
      return JBUI.CurrentTheme.ActionButton.hoverBackground()
    }
    return button.background
  }
}

private class SquareStripeButtonLookHorizontalText(button: SquareStripeButton): SquareStripeButtonLookExtension(button) {

  private var myPressedColor: Color? = null
  private var myPressedColorKey: String? = null
  private val iconLabelGap: Int
    get() = JBUI.scale(3)

  private fun getForegroundColor(): Color {
    return if (toolWindow.isActive) StripeButtonUi.SELECTED_FOREGROUND_COLOR else StripeButtonUi.FOREGROUND_COLOR
  }

  override fun paintIcon(g: Graphics?, actionButton: ActionButtonComponent?, icon: Icon) {
    // because SquareStripeButtonLook doesn't know about name and pref size contains it height we need change height for right icon layout
    val buttonWrapper = object : ActionButtonComponent {
      override fun getPopState() = actionButton!!.popState

      override fun getWidth() = actionButton!!.width

      override fun getHeight() = button.getPreferredSize().height - getLabelHeightWithGap()

      override fun getInsets() = actionButton!!.insets
    }
    val color = UIManager.getColor("ToolWindow.Button.selectedForeground")
    val iconPosition: Point
    if (!toolWindow.isActive || color == null) {
      super.paintIcon(g, buttonWrapper, icon)
      iconPosition = getIconPosition(buttonWrapper, icon)
      iconPosition.y += icon.iconHeight
    }
    else {
      val strokeIcon = toStrokeIcon(icon, color)
      super.paintIcon(g, buttonWrapper, strokeIcon)
      iconPosition = getIconPosition(buttonWrapper, strokeIcon)
      iconPosition.y += strokeIcon.iconHeight
    }

    val f = getTextFont()
    val fm = button.getFontMetrics(f)
    val texts = getStripeSplitText()
    val insets = button.insets
    val textPadding = if (UISettings.getInstance().compactMode) 4 else 6
    val textOffset = JBUI.CurrentTheme.Toolbar.stripeToolbarTextOffset(button.isOnTheLeftStripe())
    val x = insets.left + JBUI.scale(textPadding + textOffset)
    var y = iconPosition.y + iconLabelGap
    val totalWidth = button.width - insets.left - insets.right - JBUI.scale(textPadding * 2)
    val textHeight = fm.height
    var firstX: Int? = null

    for (text in texts) {
      val textWidth = UIUtil.computeStringWidth(button, fm, text)

      val g2d = g!!.create() as Graphics2D

      try {
        g2d.color = getForegroundColor()
        g2d.font = f
        UISettings.setupAntialiasing(g2d)
        if (firstX == null || texts[0].length != texts[1].length) {
          UIUtil.drawCenteredString(g2d, Rectangle(x, y, totalWidth, textHeight), text)
          firstX = x.coerceAtLeast(x + (totalWidth - fm.stringWidth(text) - 1) / 2)
        }
        else {
          g2d.drawString(text, firstX, y.coerceAtLeast(y + textHeight / 2 + fm.ascent * 2 / 5))
        }

        if (textWidth > totalWidth) {
          val gradientWidth = JBUI.scale(3)
          val gradientX = x + totalWidth - gradientWidth
          var bgColor = getBackgroundColor()

          // special case if we have hover/pressed color (0,0,0,alpha) or (255,255,255,alpha) that we don't know result bg color and will need calculate it
          if ((bgColor.red == 0 && bgColor.green == 0 && bgColor.blue == 0 ||
               bgColor.red == 255 && bgColor.green == 255 && bgColor.blue == 255) && bgColor.alpha < 255) {
            val pressedColorKey = "${button.background.rgb}:${bgColor.rgb}"
            if (myPressedColor == null || pressedColorKey != myPressedColorKey) {
              val image = UIUtil.createImage(button, 4, 4, BufferedImage.TYPE_INT_ARGB)
              val imageG = image.createGraphics()
              try {
                imageG.color = button.background
                imageG.fill(Rectangle(0, 0, 4, 4))
                imageG.color = bgColor
                imageG.fill(Rectangle(0, 0, 4, 4))
              }
              finally {
                imageG.dispose()
              }
              @Suppress("UseJBColor")
              myPressedColor = Color(image.getRGB(2, 2))
              myPressedColorKey = pressedColorKey
            }
            bgColor = myPressedColor!!
          }
          g2d.paint = GradientPaint(gradientX.toFloat(), y.toFloat(), ColorUtil.withAlpha(bgColor, 0.4),
                                    (gradientX + gradientWidth).toFloat(), y.toFloat(), bgColor)

          g2d.fill(Rectangle(gradientX, y, gradientWidth, textHeight))
        }

        y += textHeight
      }
      finally {
        g2d.dispose()
      }
    }
  }

  override fun getPreferredSize(size: Dimension): Dimension {
    size.height += getLabelHeightWithGap()
    return size
  }

  private fun getLabelHeightWithGap(): Int {
    return iconLabelGap + button.getFontMetrics(getTextFont()).height * getStripeSplitText().size
  }

  private fun getTextFont() = RelativeFont.TINY.derive(button.font)

  private fun getStripeSplitText(): List<String> {
    val text = (toolWindow.stripeShortTitleProvider?.get() ?: toolWindow.stripeTitleProvider.get()).trim()
    return text.split(" ", limit = 2)
  }
}

private fun createPresentation(toolWindow: ToolWindowImpl): Presentation {
  val presentation = Presentation(toolWindow.stripeTitleProvider)
  presentation.iconSupplier = SynchronizedClearableLazy {
    scaleIcon((toolWindow.icon ?: AllIcons.Toolbar.Unknown) as ScalableIcon)
  }
  presentation.isEnabledAndVisible = true
  return presentation
}

private fun scaleIcon(icon: ScalableIcon): Icon {
  val iconSize = JBUI.CurrentTheme.Toolbar.stripeToolbarButtonIconSize()
  return if (icon is HoledIcon && icon.icon is ScalableIcon) {
    icon.replaceBy(object : IconReplacer {
      override fun replaceIcon(icon: Icon): Icon {
        return loadIconCustomVersionOrScale(icon = icon as ScalableIcon, size = iconSize)
      }
    })
  }
  else {
    loadIconCustomVersionOrScale(icon = icon, size = iconSize)
  }
}

private fun createPopupGroup(toolWindow: ToolWindowImpl): DefaultActionGroup {
  val group = DefaultActionGroup()
  group.add(HideAction(toolWindow))
  group.addSeparator()
  group.add(createMoveGroup())
  group.addSeparator()
  if (ResizeStripeManager.enabled()) {
    group.add(ActionManager.getInstance().getAction("ToolWindowShowNamesAction")!!)
  }
  return group
}

private class HideAction(private val toolWindow: ToolWindowImpl)
  : AnAction(UIBundle.messagePointer("tool.window.new.stripe.hide.action.name")), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    toolWindow.toolWindowManager.hideToolWindow(id = toolWindow.id,
                                                hideSide = false,
                                                moveFocus = true,
                                                removeFromStripe = true,
                                                source = ToolWindowEventSource.SquareStripeButton)
  }
}

private class SquareAnActionButton(private val window: ToolWindowImpl)
  : DumbAwareToggleAction(window.stripeTitleProvider) {

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean {
    val icon = window.icon ?: AllIcons.Toolbar.Unknown
    e.presentation.icon = if (icon is ScalableIcon) scaleIcon(icon) else icon
    e.presentation.isVisible = window.isShowStripeButton && window.isAvailable
    return window.isVisible
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project
    if (project == null || project.isDisposed) {
      return
    }

    val manager = window.toolWindowManager
    if (state) {
      manager.activated(window, ToolWindowEventSource.SquareStripeButton)
    }
    else {
      manager.hideToolWindow(id = window.id,
                             hideSide = false,
                             moveFocus = true,
                             removeFromStripe = false,
                             source = ToolWindowEventSource.SquareStripeButton)
    }
  }
}

internal fun Component.isOnTheLeftStripe(): Boolean {
  val stripe = ComponentUtil.getParentOfType(ToolWindowToolbar::class.java, this)
  return stripe is ToolWindowLeftToolbar
}
