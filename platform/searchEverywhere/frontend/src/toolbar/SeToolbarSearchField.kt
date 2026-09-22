// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.toolbar

import com.intellij.accessibility.TextFieldWithListAccessibleContext
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereToolbarField
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereToolbarFields
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.setToolTipText
import com.intellij.ide.util.gotoByName.QuickSearchComponent
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.searchEverywhere.frontend.SearchEverywhereFrontendBundle
import com.intellij.platform.searchEverywhere.frontend.ui.SePopupContentPane
import com.intellij.platform.searchEverywhere.frontend.ui.SeTextField
import com.intellij.ui.ComponentUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Component
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.beans.PropertyChangeListener
import java.lang.ref.WeakReference
import java.util.Locale
import javax.accessibility.Accessible
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.accessibility.AccessibleSelection
import javax.accessibility.AccessibleStateSet
import javax.accessibility.AccessibleTable
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.event.DocumentEvent
import kotlin.math.max

/**
 * The Search Everywhere input field on a toolbar.
 *
 * The field starts a search session when it receives the focus or when the user types into it.
 * [com.intellij.platform.searchEverywhere.frontend.SeFrontendService] then shows the results popup under the field
 * and uses the field as the search field of the popup. The field clears itself when the session ends.
 *
 * During a session the field stands in for the popup content: it supplies the popup data to actions, receives the
 * quick documentation hints, and exposes the result list to assistive technology.
 *
 * @param place the action place of the toolbar that hosts the field
 */
@Internal
class SeToolbarSearchField(private val place: String)
  : SeTextField(null, true, null), SearchEverywhereToolbarField, UiDataProvider, QuickSearchComponent {
  private var sessionActive = false
  private var activationScheduled = false
  private var sessionPopup: JBPopup? = null
  private var previousFocusOwner: WeakReference<Component>? = null
  private var popupContent: SePopupContentPane? = null
  private lateinit var listAccessibleContext: SessionListAccessibleContext

  override val component: JComponent
    get() = this

  init {
    isOpaque = false
    border = UIManager.getBorder("TextField.border")
    background = UIUtil.getTextFieldBackground()
    setToolTipText(HtmlChunk.text(ActionsBundle.message("action.SearchEverywhere.description")))
    addExtension(SearchIconExtension)

    addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent) {
        if (e.isTemporary || sessionActive) {
          return
        }
        rememberPreviousFocusOwner(e.oppositeComponent)
        scheduleActivation(e.oppositeComponent, selectText = true)
      }
    })

    document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        if (!sessionActive && isFocusOwner && text.isNotEmpty()) {
          scheduleActivation(null, selectText = false)
        }
      }
    })
  }

  override fun addNotify() {
    super.addNotify()
    updatePlaceholder()
    SearchEverywhereToolbarFields.register(this)
  }

  override fun removeNotify() {
    SearchEverywhereToolbarFields.unregister(this)
    sessionPopup?.cancel()
    super.removeNotify()
  }

  override fun attachPopupContent(content: SePopupContentPane) {
    popupContent = content
    sessionListAccessibleContext().delegate = content.resultListAccessibleContext
  }

  override fun detachPopupContent(content: SePopupContentPane) {
    if (popupContent !== content) {
      return
    }
    popupContent = null
    sessionListAccessibleContext().delegate = null
  }

  override fun uiDataSnapshot(sink: DataSink) {
    popupContent?.uiDataSnapshot(sink)
  }

  override fun registerHint(h: JBPopup) {
    popupContent?.registerHint(h)
  }

  override fun unregisterHint() {
    popupContent?.unregisterHint()
  }

  override fun getAccessibleContext(): AccessibleContext? {
    if (accessibleContext == null) {
      accessibleContext = TextFieldWithListAccessibleContext(this, sessionListAccessibleContext())
    }
    return accessibleContext
  }

  private fun sessionListAccessibleContext(): SessionListAccessibleContext {
    if (!::listAccessibleContext.isInitialized) {
      listAccessibleContext = SessionListAccessibleContext(this)
    }
    return listAccessibleContext
  }

  override fun getPreferredSize(): Dimension = Dimension(JBUIScale.scale(PREFERRED_WIDTH), fieldHeight())

  override fun getMinimumSize(): Dimension = Dimension(JBUIScale.scale(MINIMUM_WIDTH), fieldHeight())

  /**
   * On the New UI main toolbar the field has the height of a toolbar widget box: the toolbar button height without the button insets.
   * Elsewhere the field keeps its own preferred height.
   */
  private fun fieldHeight(): Int {
    val ownHeight = super.getPreferredSize().height
    if (place != ActionPlaces.MAIN_TOOLBAR || !ExperimentalUI.isNewUI()) {
      return ownHeight
    }
    val buttonInsets = JBUI.CurrentTheme.Toolbar.mainToolbarButtonInsets()
    val widgetHeight = ActionToolbar.experimentalToolbarMinimumButtonSize().height - buttonInsets.top - buttonInsets.bottom
    return max(ownHeight, widgetHeight)
  }

  /**
   * Starts a search session. The service calls it before it creates the popup.
   *
   * @param searchText the text that the Search Everywhere action supplies, for example the editor selection
   * @return the initial search text of the session: [searchText], or the current text of the field when [searchText] is null
   */
  fun prepareSession(searchText: String?, selectSearchText: Boolean): String? {
    sessionActive = true
    val initialText = searchText ?: text.takeIf { it.isNotEmpty() }
    if (!isFocusOwner) {
      rememberPreviousFocusOwner(KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner)
    }
    resetSession(initialText, selectSearchText)
    IdeFocusManager.getGlobalInstance().requestFocus(this, true)
    return initialText
  }

  /**
   * Binds the results popup of the session to the field. The field cancels the popup when the toolbar removes the field.
   * The field also carries the popup as [JBPopup.KEY], so a hint that the field opens is placed next to the results popup.
   */
  fun attachPopup(popup: JBPopup) {
    sessionPopup = popup
    putClientProperty(JBPopup.KEY, popup)
  }

  /** Ends the session. Clears the field and returns the focus to the component that had it before the session. */
  fun endSession() {
    sessionPopup = null
    putClientProperty(JBPopup.KEY, null)
    sessionActive = false
    resetSession(null, true)
    if (isFocusOwner) {
      restoreFocus()
    }
    previousFocusOwner = null
  }

  private fun rememberPreviousFocusOwner(owner: Component?) {
    if (owner != null && owner !== this && !SwingUtilities.isDescendingFrom(owner, this)) {
      previousFocusOwner = WeakReference(owner)
    }
  }

  private fun scheduleActivation(contextComponent: Component?, selectText: Boolean) {
    if (activationScheduled) {
      return
    }
    activationScheduled = true
    ApplicationManager.getApplication().invokeLater({
      activationScheduled = false
      if (!sessionActive && isFocusOwner && isShowing) {
        activate(contextComponent, selectText)
      }
    }, ModalityState.stateForComponent(this))
  }

  /**
   * Runs the Search Everywhere action with this field in the data context.
   * The data context comes from [contextComponent] when it is in the same window, so the search sees the editor context.
   */
  private fun activate(contextComponent: Component?, selectText: Boolean) {
    val contextOwner = contextComponent?.takeIf { it.isShowing && ComponentUtil.getWindow(it) === ComponentUtil.getWindow(this) } ?: this
    val dataContext = CustomizedDataContext.withSnapshot(DataManager.getInstance().getDataContext(contextOwner)) { sink ->
      sink[SearchEverywhereToolbarField.DATA_KEY] = this
      @Suppress("DEPRECATION")
      sink[SearchEverywhereManagerImpl.IS_SELECT_SEARCH_TEXT] = selectText
    }
    performSearchEverywhereAction(dataContext, place, ActionUiKind.TOOLBAR, null)
  }

  private fun restoreFocus() {
    val previous = previousFocusOwner?.get()
    if (previous != null && previous.isShowing && previous.isEnabled) {
      IdeFocusManager.getGlobalInstance().requestFocus(previous, true)
      return
    }
    val project = ProjectUtil.getProjectForComponent(this)
    if (project == null) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner()
      return
    }
    val toolWindowManager = ToolWindowManager.getInstance(project)
    toolWindowManager.activateEditorComponent()
    SwingUtilities.invokeLater {
      if (!toolWindowManager.isEditorComponentActive && isFocusOwner) {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner()
      }
    }
  }

  /** The placeholder is the feature name, so it keeps the title case. */
  @Suppress("DialogTitleCapitalization")
  private fun updatePlaceholder() {
    emptyText.clear()
    emptyText.appendText(SearchEverywhereFrontendBundle.bundle.getMessage("search.everywhere.toolbar.field.placeholder"))
    val shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_SEARCH_EVERYWHERE)
    if (shortcut.isNotEmpty()) {
      emptyText.appendText("  ").appendText(shortcut, SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
  }

  /**
   * Forwards to the accessible context of the result list of the active session.
   * Without a session it reports an empty list. It keeps the property change listeners across sessions.
   */
  private class SessionListAccessibleContext(private val owner: Component) : AccessibleContext() {
    private val listeners = ArrayList<PropertyChangeListener>()

    var delegate: AccessibleContext? = null
      set(value) {
        if (field === value) {
          return
        }
        field?.let { old -> listeners.forEach(old::removePropertyChangeListener) }
        field = value
        value?.let { new -> listeners.forEach(new::addPropertyChangeListener) }
      }

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
      listeners.add(listener)
      delegate?.addPropertyChangeListener(listener)
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
      listeners.remove(listener)
      delegate?.removePropertyChangeListener(listener)
    }

    override fun getAccessibleRole(): AccessibleRole = delegate?.accessibleRole ?: AccessibleRole.LIST

    override fun getAccessibleStateSet(): AccessibleStateSet = delegate?.accessibleStateSet ?: AccessibleStateSet()

    override fun getAccessibleIndexInParent(): Int = delegate?.accessibleIndexInParent ?: -1

    override fun getAccessibleChildrenCount(): Int = delegate?.accessibleChildrenCount ?: 0

    override fun getAccessibleChild(i: Int): Accessible? = delegate?.getAccessibleChild(i)

    override fun getLocale(): Locale = delegate?.locale ?: owner.locale

    override fun getAccessibleSelection(): AccessibleSelection? = delegate?.accessibleSelection

    override fun getAccessibleTable(): AccessibleTable? = delegate?.accessibleTable
  }

  private object SearchIconExtension : ExtendableTextComponent.Extension {
    override fun getIcon(hovered: Boolean): Icon = AllIcons.Actions.Search

    override fun isIconBeforeText(): Boolean = true

    override fun getIconGap(): Int = JBUIScale.scale(6)
  }

  private companion object {
    const val PREFERRED_WIDTH: Int = 350
    const val MINIMUM_WIDTH: Int = 150
  }
}
