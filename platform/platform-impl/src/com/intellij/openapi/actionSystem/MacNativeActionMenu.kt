// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.ide.DataManager
import com.intellij.ide.ui.UISettings
import com.intellij.internal.inspector.UiInspectorContextProvider
import com.intellij.internal.inspector.UiInspectorUtil
import com.intellij.openapi.actionSystem.impl.ActionMenu.Companion.showDescriptionInStatusBar
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.actionSystem.impl.actionholder.createActionRef
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBMenu
import com.intellij.ui.icons.getMenuBarIcon
import com.intellij.ui.mac.foundation.NSDefaults
import com.intellij.ui.mac.screenmenu.Menu
import com.intellij.ui.plaf.beg.IdeaMenuUI
import com.intellij.util.FontUtil
import com.intellij.util.ReflectionUtil
import java.awt.Window
import java.util.concurrent.ScheduledFuture
import javax.swing.ButtonModel
import javax.swing.JMenu
import javax.swing.JPopupMenu
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import javax.swing.event.MenuEvent
import javax.swing.event.MenuListener

@Suppress("RedundantConstructorKeyword")
class MacNativeActionMenu constructor(private val context: DataContext?,
                                      private val place: String,
                                      group: ActionGroup,
                                      private val presentationFactory: PresentationFactory,
                                      private var isMnemonicEnabled: Boolean,
                                      private val useDarkIcons: Boolean) : JBMenu() {
  private val group = createActionRef(group)
  private val presentation = presentationFactory.getPresentation(group)

  @JvmField
  val screenMenuPeer: Menu = Menu(presentation.getText(isMnemonicEnabled))

  val anAction: AnAction
    get() = group.getAction()

  private var specialMenu: JPopupMenu? = null

  init {
    screenMenuPeer.setOnOpen(Runnable {
      // NOTE: setSelected(true) calls fillMenu internally
      setSelected(true)
    }, this)
    screenMenuPeer.setOnClose(Runnable { setSelected(false) }, this)
    screenMenuPeer.listenPresentationChanges(presentation)

    init()
  }

  override fun getPopupMenu(): JPopupMenu {
    var specialMenu = specialMenu
    if (specialMenu == null) {
      specialMenu = JBPopupMenu()
      this.specialMenu = specialMenu
      specialMenu.setInvoker(this)
      popupListener = createWinListener(specialMenu)
      ReflectionUtil.setField(JMenu::class.java, this, JPopupMenu::class.java, "popupMenu", specialMenu)
      UiInspectorUtil.registerProvider(specialMenu, UiInspectorContextProvider {
        UiInspectorUtil.collectActionGroupInfo("Menu", group.getAction(), place)
      })
    }
    return super.getPopupMenu()
  }

  override fun updateUI() {
    // null myPlace means that Swing calls updateUI before our constructor
    @Suppress("SENSELESS_COMPARISON")
    if (screenMenuPeer != null || place == null) {
      return
    }

    setUI(IdeaMenuUI.createUI(this))
    setFont(FontUtil.getMenuFont())
    getPopupMenu().updateUI()
  }

  private fun init() {
    setBorderPainted(false)
    val menuListener = MenuListenerImpl()
    addMenuListener(menuListener)
    getModel().addChangeListener(menuListener)
    updateFromPresentation(isMnemonicEnabled)
  }

  fun updateFromPresentation(enableMnemonics: Boolean) {
    isMnemonicEnabled = enableMnemonics
    isVisible = presentation.isVisible
    setEnabled(presentation.isEnabled)
    setText(presentation.getText(isMnemonicEnabled))
    mnemonic = presentation.getMnemonic()
    displayedMnemonicIndex = presentation.getDisplayedMnemonicIndex()
    updateIcon()
  }

  override fun setDisplayedMnemonicIndex(index: Int) {
    super.setDisplayedMnemonicIndex(if (isMnemonicEnabled) index else -1)
  }

  override fun setMnemonic(mnemonic: Int) {
    super.setMnemonic(if (isMnemonicEnabled) mnemonic else 0)
  }

  private fun updateIcon() {
    if (ExperimentalUI.isNewUI() ||
        Registry.get("ide.macos.main.menu.alignment.options").isOptionEnabled("No icons") ||
        !UISettings.getInstance().showIconsInMenus) {
      return
    }

    // JDK can't correctly paint our HiDPI icons at the system menu bar
    val icon = getMenuBarIcon(presentation.icon ?: return, useDarkIcons)
    setIcon(icon)
    screenMenuPeer.setIcon(icon)
  }

  override fun menuSelectionChanged(isIncluded: Boolean) {
    super.menuSelectionChanged(isIncluded)
    showDescriptionInStatusBar(isIncluded = isIncluded, component = this, description = presentation.description)
  }

  private inner class MenuListenerImpl : ChangeListener, MenuListener {
    var delayedClear: ScheduledFuture<*>? = null
    var isSelected: Boolean = false

    override fun stateChanged(e: ChangeEvent) {
      // Re-implement javax.swing.JMenu.MenuChangeListener to avoid recursive event notifications
      // if 'menuSelected' fires unrelated 'stateChanged' event, without changing 'model.isSelected()' value.
      val model = e.source as ButtonModel
      val modelSelected = model.isSelected
      if (modelSelected != isSelected) {
        isSelected = modelSelected
        if (modelSelected) {
          menuSelected()
        }
        else {
          menuDeselected()
        }
      }
    }

    override fun menuCanceled(e: MenuEvent) {
    }

    override fun menuDeselected(e: MenuEvent) {
      // Use ChangeListener instead to guard against recursive calls
    }

    override fun menuSelected(e: MenuEvent) {
      // Use ChangeListener instead to guard against recursive calls
    }

    private fun menuDeselected() {
    }

    private fun menuSelected() {
      if (delayedClear != null) {
        delayedClear!!.cancel(false)
        delayedClear = null
      }
      if (SystemInfo.isMacSystemMenu && ActionPlaces.MAIN_MENU == place) {
        fillMenu()
      }
    }
  }

  override fun setPopupMenuVisible(value: Boolean) {
    if (value && !(SystemInfo.isMacSystemMenu && ActionPlaces.MAIN_MENU == place)) {
      fillMenu()
      if (!isSelected) {
        return
      }
    }

    super.setPopupMenuVisible(value)
  }

  private fun getDataContext(): DataContext {
    var context = context
    if (context != null) {
      return context
    }

    val dataManager = DataManager.getInstance()
    @Suppress("DEPRECATION")
    context = dataManager.getDataContext()
    if (PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(context) == null) {
      val frame = ComponentUtil.getParentOfType(IdeFrame::class.java, this)
      context = dataManager.getDataContext(IdeFocusManager.getGlobalInstance().getLastFocusedFor(frame as Window?))
    }
    return Utils.wrapDataContext(context)
  }

  fun fillMenu() {
    val context = getDataContext()
    val isDarkMenu = SystemInfo.isMacSystemMenu && NSDefaults.isDarkMenuBar()
    Utils.fillMenu(/* group = */ group.getAction(),
                   /* component = */ this,
                   /* enableMnemonics = */ isMnemonicEnabled,
                   /* presentationFactory = */ presentationFactory,
                   /* context = */ context,
                   /* place = */ place,
                   /* isWindowMenu = */ true,
                   /* useDarkIcons = */ isDarkMenu,
                   /* progressPoint = */ RelativePoint.getNorthEastOf(this)) { !isSelected }
  }
}
