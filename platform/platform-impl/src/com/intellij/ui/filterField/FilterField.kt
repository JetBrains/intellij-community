package com.intellij.ui.filterField

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.AutoPopupSupportingListener
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ClickListener
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.*
import com.intellij.util.ui.accessibility.AccessibleContextDelegate
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.*
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
import java.util.function.Supplier
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.*
import javax.swing.border.Border

abstract class FilterField(
  @Nls(capitalization = Nls.Capitalization.Title) val originalMessage: String
) : JPanel() {

  private val nameLabel: JLabel = object : JLabel() {
    override fun getText(): String {
      val value = getCurrentText()
      return if (value == null) "$originalMessage " else "$originalMessage: "
    }
  }

  private val valueLabel: JLabel = object : JLabel() {
    override fun getText(): String? {
      return getCurrentText()
    }
  }

  init {
    initUi()
  }

  private fun initUi() {
    this.isFocusable = true
    this.layout = BoxLayout(this, BoxLayout.X_AXIS)
    this.border = wrapBorder(createUnfocusedBorder())

    this.add(nameLabel)
    this.add(valueLabel)
    this.add(JLabel(AllIcons.General.ArrowDown))

    nameLabel.foreground = getDefaultNameForeground()
    valueLabel.foreground = getDefaultSelectorForeground()

    showPopupMenuOnClick()
    showPopupMenuFromKeyboard()
    indicateHovering()
    indicateFocusing()

    this.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
  }

  @RequiresBackgroundThread
  @RequiresReadLock
  protected abstract fun buildActions(): Collection<AnAction>

  @Nls
  abstract fun getCurrentText(): String?

  private fun indicateFocusing() {
    addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent) {
        border = wrapBorder(createFocusedBorder())
      }

      override fun focusLost(e: FocusEvent) {
        border = wrapBorder(createUnfocusedBorder())
      }
    })
  }

  private fun showPopupMenuFromKeyboard() {
    addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent) {
        if (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_DOWN) {
          showPopupMenu()
        }
      }
    })
  }

  private fun showPopupMenuOnClick() {
    object : ClickListener() {
      override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
        showPopupMenu()
        return true
      }
    }.installOn(this)
  }

  private fun indicateHovering() {
    addMouseListener(object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) {
        setOnHoverForeground()
      }

      override fun mouseExited(e: MouseEvent) {
        setDefaultForeground()
      }
    })
  }

  private fun showPopupMenu() {
    showAsyncChooserPopup(
      this@FilterField,
      ::buildActions,
      DataManager.getInstance().getDataContext(this)
    )
  }

  private fun setDefaultForeground() {
    nameLabel.foreground = getDefaultNameForeground()
    valueLabel.foreground = getDefaultSelectorForeground()
  }

  private fun getDefaultNameForeground(): Color {
    return UIUtil.getLabelInfoForeground()
  }

  private fun getDefaultSelectorForeground(): Color {
    return if (StartupUiUtil.isDarkTheme) UIUtil.getLabelForeground() else NamedColorUtil.getInactiveTextColor().darker().darker()
  }

  private fun setOnHoverForeground() {
    nameLabel.foreground = if (StartupUiUtil.isDarkTheme) UIUtil.getLabelForeground() else UIUtil.getTextAreaForeground()
    valueLabel.foreground = if (StartupUiUtil.isDarkTheme) UIUtil.getLabelForeground() else UIUtil.getTextFieldForeground()
  }

  private fun createFocusedBorder(): Border {
    return FilledRoundedBorder(UIUtil.getFocusedBorderColor(), 10, 2)
  }

  protected open fun createUnfocusedBorder(): Border {
    return JBUI.Borders.empty(2)
  }

  private fun wrapBorder(outerBorder: Border): Border {
    return BorderFactory.createCompoundBorder(outerBorder, JBUI.Borders.empty(2))
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = AccessiblePopupComponent(super.getAccessibleContext())
    }
    return accessibleContext
  }

  private inner class AccessiblePopupComponent(context: AccessibleContext) : AccessibleContextDelegate(context) {
    override fun getDelegateParent(): Container? = null
    override fun getAccessibleRole(): AccessibleRole = AccessibleRole.POPUP_MENU

    override fun getAccessibleName(): String {
      return IdeBundle.message("accessibility.filter.label", nameLabel.text, valueLabel.text)
    }
  }

  private class FilledRoundedBorder(private val myColor: Color, private val myArcSize: Int, private val myThickness: Int) : Border {
    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
      val config = GraphicsUtil.setupAAPainting(g)
      g.color = myColor
      val thickness = JBUI.scale(myThickness)
      val arcSize = JBUI.scale(myArcSize)
      val area = Area(RoundRectangle2D.Double(x.toDouble(), y.toDouble(),
                                              width.toDouble(), height.toDouble(),
                                              arcSize.toDouble(), arcSize.toDouble()))
      val innerArc = (arcSize - thickness).coerceAtLeast(0)
      area.subtract(Area(RoundRectangle2D.Double((x + thickness).toDouble(), (y + thickness).toDouble(),
                                                 (width - 2 * thickness).toDouble(),
                                                 (height - 2 * thickness).toDouble(),
                                                 innerArc.toDouble(), innerArc.toDouble())))
      (g as Graphics2D).fill(area)
      config.restore()
    }

    override fun getBorderInsets(c: Component): Insets {
      return JBUI.insets(myThickness)
    }

    override fun isBorderOpaque(): Boolean = false
  }
}

private fun showAsyncChooserPopup(component: JComponent,
                                  itemsLoader: Supplier<Collection<AnAction>>,
                                  dataContext: DataContext) {
  val popup = JBPopupFactory.getInstance().createActionGroupPopup(
    null,
    LoadingActionGroup(itemsLoader),
    dataContext,
    JBPopupFactory.ActionSelectionAid.MNEMONICS,
    false
  )

  AutoPopupSupportingListener.installOn(popup)
  popup.showUnderneathOf(component)
}

private class LoadingActionGroup(val supplier: Supplier<Collection<AnAction>>) : ActionGroup() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    return supplier.get().toTypedArray()
  }
}
