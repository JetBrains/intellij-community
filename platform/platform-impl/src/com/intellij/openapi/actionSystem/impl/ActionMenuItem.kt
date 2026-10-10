// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ExperimentalIconRendering
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.ActionIconDescriptors
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionMenu.Companion.isAligned
import com.intellij.openapi.actionSystem.impl.ActionMenu.Companion.isAlignedInGroup
import com.intellij.openapi.actionSystem.impl.ActionMenu.Companion.isShowNoIcons
import com.intellij.openapi.actionSystem.impl.ActionMenu.Companion.shouldConvertIconToDarkVariant
import com.intellij.openapi.actionSystem.impl.ActionMenu.Companion.showDescriptionInStatusBar
import com.intellij.openapi.actionSystem.impl.actionholder.createActionRef
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.TransactionGuardImpl
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.getShortcutSetForDisplay
import com.intellij.openapi.util.IconLoader.getDarkIcon
import com.intellij.openapi.util.IconLoader.getDisabledIcon
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.forceDarkVariant
import com.intellij.platform.icons.modifiers.forceLightVariant
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.ui.components.JBCheckBoxMenuItem
import com.intellij.ui.icons.getMenuBarIcon
import com.intellij.ui.mac.MacMenuSettings
import com.intellij.ui.mac.screenmenu.Menu
import com.intellij.ui.mac.screenmenu.MenuItem
import com.intellij.ui.plaf.beg.BegMenuItemUI
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.LafIconLookup.getDisabledIcon
import com.intellij.util.ui.LafIconLookup.getIcon
import com.intellij.util.ui.LafIconLookup.getSelectedIcon
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.Icon
import javax.swing.JMenuItem
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

internal fun isEnterKeyStroke(keyStroke: KeyStroke): Boolean {
  return keyStroke.keyCode == KeyEvent.VK_ENTER && keyStroke.modifiers == 0
}

class ActionMenuItem internal constructor(action: AnAction,
                                          private val context: DataContext,
                                          @JvmField val place: String,
                                          private val uiKind: ActionUiKind.Popup,
                                          private val enableMnemonics: Boolean,
                                          private val insideCheckedGroup: Boolean,
                                          private val useDarkIcons: Boolean) : JBCheckBoxMenuItem() {

  private val actionRef = createActionRef(action)
  // do not expose presentation
  private val presentation = Presentation.newTemplatePresentation()

  val isToggleable: Boolean = action is Toggleable

  @JvmField
  internal val screenMenuItemPeer: MenuItem?

  @NlsSafe
  private var description: String? = null
  private var isToggled = false
  var keepPopupOnPerform: KeepPopupOnPerform = KeepPopupOnPerform.Never
    private set
  val secondaryIcon: Icon?
    get() = if (UISettings.getInstance().showIconsInMenus) presentation.getClientProperty(ActionUtil.SECONDARY_ICON) else null

  init {
    addActionListener(ActionListener { e -> performAction(e.modifiers) })
    setBorderPainted(false)
    if (Menu.isJbScreenMenuEnabled() && ActionPlaces.MAIN_MENU == this.place) {
      screenMenuItemPeer = MenuItem()
      screenMenuItemPeer.setActionDelegate(Runnable {

        // Called on AppKit when user activates menu item
        if (isToggleable) {
          isToggled = !isToggled
          screenMenuItemPeer.setState(isToggled)
        }
        SwingUtilities.invokeLater(Runnable {
          if (presentation.isEnabledInModalContext || this.context.getData(PlatformCoreDataKeys.IS_MODAL_CONTEXT) != true) {
            (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity(
              Runnable { performAction(0) })
          }
        })
      })
    }
    else {
      screenMenuItemPeer = null
    }
    updateUI()
    updateAccelerator()
  }

  val anAction: AnAction
    get() = actionRef.getAction()

  public override fun fireActionPerformed(event: ActionEvent) {
    (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity(
      Runnable { super.fireActionPerformed(event) })
  }

  private fun updateAccelerator() {
    val action = actionRef.getAction()
    setAcceleratorFromShortcuts(getShortcutSetForDisplay(action).getShortcuts())
  }

  fun updateFromPresentation(presentation: Presentation) {
    this.presentation.copyFrom(presentation, null, true)
    // all items must be visible at this point
    //setVisible(presentation.isVisible());
    setEnabled(presentation.isEnabled)
    val text = ActionPresentationDecorator.decorateTextIfNeeded(actionRef.getAction(), presentation.getText(enableMnemonics))
    setText(text)
    mnemonic = presentation.getMnemonic()
    displayedMnemonicIndex = presentation.getDisplayedMnemonicIndex()
    updateIcon(presentation)
    description = presentation.description
    keepPopupOnPerform = presentation.keepPopupOnPerform
    if (screenMenuItemPeer != null) {
      screenMenuItemPeer.setLabel(text, accelerator)
      screenMenuItemPeer.setEnabled(isEnabled)
    }
    val shortcutSuffix = presentation.getClientProperty(ActionUtil.KEYBOARD_SHORTCUT_SUFFIX)
    val shortcut = defaultFirstShortcutText
    firstShortcutTextFromPresentation = if (shortcut.isNotEmpty() && !shortcutSuffix.isNullOrEmpty()) {
      shortcut + shortcutSuffix
    }
    else {
      null
    }
  }

  @Throws(IllegalArgumentException::class)
  override fun setDisplayedMnemonicIndex(index: Int) {
    super.setDisplayedMnemonicIndex(if (enableMnemonics) index else -1)
  }

  override fun setMnemonic(mnemonic: Int) {
    super.setMnemonic(if (enableMnemonics) mnemonic else 0)
  }

  private fun setAcceleratorFromShortcuts(shortcuts: Array<Shortcut>) {
    for (shortcut in shortcuts) {
      if (shortcut is KeyboardShortcut) {
        val firstKeyStroke = shortcut.firstKeyStroke
        // If the action has `Enter` shortcut, do not add it. Otherwise, user won't be able to choose any ActionMenuItem other than that
        if (!isEnterKeyStroke(firstKeyStroke)) {
          setAccelerator(firstKeyStroke)
          screenMenuItemPeer?.setLabel(text, firstKeyStroke)
          if (KeymapUtil.isSimplifiedMacShortcuts) {
            val shortcutText = KeymapUtil.getPreferredShortcutText(shortcuts)
            putClientProperty("accelerator.text", shortcutText)
            screenMenuItemPeer?.setAcceleratorText(shortcutText)
          }
        }
        break
      }
    }
  }

  override fun updateUI() {
    setUI(BegMenuItemUI.createUI(this))
  }

  /**
   * Updates long description of action at the status bar.
   */
  override fun menuSelectionChanged(isIncluded: Boolean) {
    super.menuSelectionChanged(isIncluded)
    showDescriptionInStatusBar(isIncluded = isIncluded, component = this, description = description)
  }

  private var firstShortcutTextFromPresentation: @NlsSafe String? = null

  private val defaultFirstShortcutText: @NlsSafe String
    get() = KeymapUtil.getShortcutText(getShortcutSetForDisplay(actionRef.getAction()))

  val firstShortcutText: @NlsSafe String
    get() = firstShortcutTextFromPresentation ?: defaultFirstShortcutText

  private fun updateIcon(presentation: Presentation) {
    isToggled = isToggleable && Toggleable.isSelected(presentation)
    val showIcons = UISettings.getInstance().showIconsInMenus
    if (!isToggleable && !showIcons) return
    val darkOverride = menuIconDarkOverride(place, useDarkIcons)
    val baseIcon = menuIcon(presentation, darkOverride)
    if (isToggleable && (baseIcon == null || insideCheckedGroup || !showIcons)) {
      if (ActionPlaces.MAIN_MENU == place && MacMenuSettings.isSystemMenu) {
        state = isToggled
        screenMenuItemPeer?.setState(isToggled)
      }
      val adjustedIcon = adjustIcon(baseIcon, presentation)
      if (adjustedIcon != null) {
        setIcon(adjustedIcon)
        if (ExperimentalIconRendering.isEnabled) {
          setDisabledIcon(adjustIcon(menuDisabledIcon(presentation, darkOverride), presentation))
        }
      }
      else if (isToggled) {
        setToggledIcon()
      }
      else {
        setIcon(EmptyIcon.ICON_16)
        setSelectedIcon(EmptyIcon.ICON_16)
        setDisabledIcon(EmptyIcon.ICON_16)
      }
    }
    else if (showIcons) {
      var icon = baseIcon
      if (isToggleable && isToggled && icon != null) {
        icon = PoppedIcon(icon, 16, 16)
      }
      var disabled = framedDisabledIcon(menuDisabledIcon(presentation, darkOverride), isToggleable && isToggled)
      if (disabled == null) {
        disabled = if (icon == null) null else getDisabledIcon(icon)
      }
      var selected = menuSelectedIcon(presentation, darkOverride)
      if (selected == null) {
        selected = icon
      }
      setIcon(adjustIcon(if (presentation.isEnabled) icon else disabled, presentation))
      setSelectedIcon(adjustIcon(selected, presentation))
      setDisabledIcon(adjustIcon(disabled, presentation))
    }
  }

  private fun adjustIcon(icon: Icon?, presentation: Presentation): Icon? {
    val isMainMenu = ActionPlaces.MAIN_MENU == place
    return when {
      isMainMenu && isShowNoIcons(actionRef.getAction(), presentation) -> null
      !isAligned || !isAlignedInGroup -> icon
      isMainMenu && icon == null && MacMenuSettings.isSystemMenu -> EMPTY_MENU_ACTION_ICON
      else -> icon
    }
  }

  override fun setIcon(icon: Icon?) {
    var effectiveIcon: Icon? = icon
    if (effectiveIcon != null) {
      if (MacMenuSettings.isSystemMenu && ActionPlaces.MAIN_MENU == place) {
        // JDK can't correctly paint our HiDPI icons at the system menu bar
        effectiveIcon = getMenuBarIcon(effectiveIcon, useDarkIcons)
      }
      else if (shouldConvertIconToDarkVariant()) {
        effectiveIcon = getDarkIcon(effectiveIcon, true)
      }
    }
    super.setIcon(effectiveIcon)
    screenMenuItemPeer?.setIcon(effectiveIcon)
  }

  override fun isSelected(): Boolean = isToggled

  private fun performAction(modifiers: Int) {
    val id = ActionManager.getInstance().getId(actionRef.getAction())
    if (id != null) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("context.menu.click.stats.${id.replace(' ', '.')}")
    }
    IdeFocusManager.findInstanceByContext(context).runOnOwnContext(context) {
      val action = actionRef.getAction()
      val currentEvent = IdeEventQueue.getInstance().trueCurrentEvent
      val event = AnActionEvent(
        context, presentation.clone(), place, uiKind,
        currentEvent as? InputEvent, modifiers,
        ActionManager.getInstance())
      ActionUtil.performAction(action, event)
    }
  }
}

internal fun JMenuItem.setToggledIcon() {
  var checkmark = getIcon("checkmark")
  var selectedCheckmark = getSelectedIcon("checkmark")
  var disabledCheckmark = getDisabledIcon("checkmark")
  if (shouldConvertIconToDarkVariant()) {
    checkmark = getDarkIcon(checkmark, true)
    selectedCheckmark = getDarkIcon(selectedCheckmark, true)
    disabledCheckmark = getDarkIcon(disabledCheckmark, true)
  }
  setIcon(checkmark)
  setSelectedIcon(selectedCheckmark)
  setDisabledIcon(disabledCheckmark)
}

/**
 * The icon of a menu item in the normal state.
 * [darkOverride] chooses the artwork of a descriptor icon, see [menuIconDarkOverride]. The legacy icons ignore it.
 */
internal fun menuIcon(presentation: Presentation, darkOverride: Boolean? = null): Icon? =
  if (ExperimentalIconRendering.isEnabled) {
    ActionIconDescriptors.effective(presentation)?.withDarkOverride(darkOverride)?.createSwingIcon()
  }
  else {
    presentation.icon
  }

/**
 * The icon of a menu item in the disabled state.
 * While the new icons pipeline is on, this is never `null` when [menuIcon] is not.
 * Callers rely on this rule, so their `getDisabledIcon` fallback never gets a bridged icon.
 * [darkOverride] chooses the artwork of a descriptor icon, see [menuIconDarkOverride]. The legacy icons ignore it.
 */
internal fun menuDisabledIcon(presentation: Presentation, darkOverride: Boolean? = null): Icon? =
  if (ExperimentalIconRendering.isEnabled) {
    ActionIconDescriptors.effective(presentation)?.let {
      ActionIconDescriptors.disabled(it.withDarkOverride(darkOverride)).createSwingIcon()
    }
  }
  else {
    presentation.disabledIcon
  }

/**
 * The icon of a menu item in the selected state.
 * [darkOverride] chooses the artwork of a descriptor icon, see [menuIconDarkOverride]. The legacy icons ignore it.
 */
internal fun menuSelectedIcon(presentation: Presentation, darkOverride: Boolean? = null): Icon? =
  if (ExperimentalIconRendering.isEnabled) {
    presentation.selectedIconDescriptor?.withDarkOverride(darkOverride)?.createSwingIcon()
  }
  else {
    presentation.selectedIcon
  }

private fun IconDescriptor.withDarkOverride(darkOverride: Boolean?): IconDescriptor =
  when (darkOverride) {
    null -> this
    true -> iconDescriptor { icon(this@withDarkOverride, IconModifier.forceDarkVariant()) }
    false -> iconDescriptor { icon(this@withDarkOverride, IconModifier.forceLightVariant()) }
  }

/**
 * The artwork that the icons of a menu item must use, whatever the theme says.
 * `true` forces the dark artwork, `false` forces the light artwork, and `null` lets the theme choose.
 *
 * The macOS system menu bar has its own darkness. [useDarkIcons] holds it.
 * A bright theme can also have a dark menu background.
 * The legacy icons get these cases through `getMenuBarIcon` and `getDarkIcon`, which do nothing for the new icons.
 */
internal fun menuIconDarkOverride(place: String, useDarkIcons: Boolean): Boolean? =
  when {
    MacMenuSettings.isSystemMenu && ActionPlaces.MAIN_MENU == place -> useDarkIcons
    shouldConvertIconToDarkVariant() -> true
    else -> null
  }

/**
 * Adds the toggled frame to the [disabled] icon of a toggled menu item.
 *
 * Only the new icons pipeline needs this. There, [menuDisabledIcon] builds the icon from the descriptor, so it has no frame.
 * The legacy fallback `getDisabledIcon(icon)` runs on the framed icon, so it keeps the frame.
 * With the pipeline off, this function returns [disabled] as it is.
 */
internal fun framedDisabledIcon(disabled: Icon?, isToggled: Boolean): Icon? =
  if (disabled != null && isToggled && ExperimentalIconRendering.isEnabled) PoppedIcon(disabled, 16, 16) else disabled

/** The [ActionMenuItem] constructor is internal, so tests in other modules create an item here. */
@TestOnly
@ApiStatus.Internal
fun createActionMenuItemForTest(
  action: AnAction,
  place: String = "TestPlace",
  insideCheckedGroup: Boolean = false,
): ActionMenuItem =
  ActionMenuItem(
    action,
    DataContext.EMPTY_CONTEXT,
    place,
    ActionUiKind.POPUP,
    enableMnemonics = false,
    insideCheckedGroup = insideCheckedGroup,
    useDarkIcons = false,
  )
