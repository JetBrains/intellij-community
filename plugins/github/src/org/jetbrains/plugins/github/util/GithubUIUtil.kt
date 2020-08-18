// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.UtilBundle
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.changes.issueLinks.LinkMouseListenerBase
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.*
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.speedSearch.NameFilteringListModel
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.*
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.GithubIcons
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.event.*
import java.beans.PropertyChangeListener
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.swing.*
import javax.swing.event.DocumentEvent

object GithubUIUtil {
  val avatarSize = JBUI.uiIntValue("Github.Avatar.Size", 20)

  fun getPullRequestStateIcon(state: GHPullRequestState, isDraft: Boolean): Icon =
    if (isDraft) GithubIcons.PullRequestDraft
    else when (state) {
      GHPullRequestState.CLOSED -> GithubIcons.PullRequestClosed
      GHPullRequestState.MERGED -> GithubIcons.PullRequestMerged
      GHPullRequestState.OPEN -> GithubIcons.PullRequestOpen
    }

  fun getPullRequestStateText(state: GHPullRequestState, isDraft: Boolean): String =
    if (isDraft) GithubBundle.message("pull.request.state.draft")
    else when (state) {
      GHPullRequestState.CLOSED -> GithubBundle.message("pull.request.state.closed")
      GHPullRequestState.MERGED -> GithubBundle.message("pull.request.state.merged")
      GHPullRequestState.OPEN -> GithubBundle.message("pull.request.state.open")
    }

  fun getIssueStateIcon(state: GithubIssueState): Icon =
    when (state) {
      GithubIssueState.open -> GithubIcons.IssueOpened
      GithubIssueState.closed -> GithubIcons.IssueClosed
    }

  fun getIssueStateText(state: GithubIssueState): String =
    when (state) {
      GithubIssueState.open -> GithubBundle.message("issue.state.open")
      GithubIssueState.closed -> GithubBundle.message("issue.state.closed")
    }

  fun <T : JComponent> overrideUIDependentProperty(component: T, listener: T.() -> Unit) {
    component.addPropertyChangeListener("UI", PropertyChangeListener {
      listener.invoke(component)
    })
    listener.invoke(component)
  }

  fun focusPanel(panel: JComponent) {
    val focusManager = IdeFocusManager.findInstanceByComponent(panel)
    val toFocus = focusManager.getFocusTargetFor(panel) ?: return
    focusManager.doWhenFocusSettlesDown { focusManager.requestFocus(toFocus, true) }
  }

  fun createIssueLabelLabel(label: GHLabel): JBLabel = JBLabel(" ${label.name} ", UIUtil.ComponentStyle.SMALL).apply {
    background = getLabelBackground(label)
    foreground = getLabelForeground(background)
  }.andOpaque()

  fun getLabelBackground(label: GHLabel): JBColor {
    val apiColor = ColorUtil.fromHex(label.color)
    return JBColor(apiColor, ColorUtil.darker(apiColor, 3))
  }

  fun getLabelForeground(bg: Color): Color = if (ColorUtil.isDark(bg)) Color.white else Color.black

  fun getFontEM(component: JComponent): Float {
    val metrics = component.getFontMetrics(component.font)
    //em dash character
    return FontLayoutService.getInstance().charWidth2D(metrics, '\u2014'.toInt())
  }

  fun formatActionDate(date: Date): String {
    val prettyDate = DateFormatUtil.formatPrettyDate(date).toLowerCase()
    val datePrefix = if (prettyDate.equals(UtilBundle.message("date.format.today"), true) ||
                         prettyDate.equals(UtilBundle.message("date.format.yesterday"), true)) ""
    else "on "
    return datePrefix + prettyDate
  }

  fun createNoteWithAction(action: () -> Unit): SimpleColoredComponent {
    return SimpleColoredComponent().apply {
      isFocusable = true
      isOpaque = false

      LinkMouseListenerBase.installSingleTagOn(this)
      registerKeyboardAction({ action() },
                             KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                             JComponent.WHEN_FOCUSED)
    }
  }

  object Lists {
    fun installSelectionOnFocus(list: JList<*>): FocusListener {
      val listener: FocusListener = object : FocusAdapter() {
        override fun focusGained(e: FocusEvent) {
          if (list.isSelectionEmpty && list.model.size > 0) list.selectedIndex = 0
        }
      }
      list.addFocusListener(listener)
      return listener
    }

    fun installSelectionOnRightClick(list: JList<*>): MouseListener {
      val listener: MouseListener = object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
          if (SwingUtilities.isRightMouseButton(e)) {
            val row = list.locationToIndex(e.point)
            if (row != -1) list.selectedIndex = row
          }
        }
      }
      list.addMouseListener(listener)
      return listener
    }
  }

  fun <T> showChooserPopup(popupTitle: String, parentComponent: JComponent,
                           cellRendererFactory: (JList<SelectableWrapper<T>>) -> SelectionListCellRenderer<T>,
                           currentList: List<T>,
                           availableListFuture: CompletableFuture<List<T>>)
    : CompletableFuture<CollectionDelta<T>> {

    val listModel = CollectionListModel<SelectableWrapper<T>>()
    val list = JBList<SelectableWrapper<T>>().apply {
      visibleRowCount = 7
      isFocusable = false
      selectionMode = ListSelectionModel.SINGLE_SELECTION
    }
    val listCellRenderer = cellRendererFactory(list)
    list.cellRenderer = listCellRenderer

    val speedSearch = SpeedSearch()
    val filteringListModel = NameFilteringListModel<SelectableWrapper<T>>(listModel, { listCellRenderer.getText(it.value) },
                                                                          speedSearch::shouldBeShowing, { speedSearch.filter ?: "" })
    list.model = filteringListModel

    speedSearch.addChangeListener {
      val prevSelection = list.selectedValue // save to restore the selection on filter drop
      filteringListModel.refilter()
      if (filteringListModel.size > 0) {
        val fullMatchIndex = if (speedSearch.isHoldingFilter) filteringListModel.closestMatchIndex
        else filteringListModel.getElementIndex(prevSelection)
        if (fullMatchIndex != -1) {
          list.selectedIndex = fullMatchIndex
        }

        if (filteringListModel.size <= list.selectedIndex || !filteringListModel.contains(list.selectedValue)) {
          list.selectedIndex = 0
        }
      }
    }

    val scrollPane = ScrollPaneFactory.createScrollPane(list, true).apply {
      viewport.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      isFocusable = false
    }

    val searchField = SearchTextField(false).apply {
      border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
      UIUtil.setBackgroundRecursively(this, UIUtil.getListBackground())
      textEditor.border = JBUI.Borders.empty()
      //focus dark magic, otherwise focus shifts to searchfield panel
      isFocusable = false
      addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          speedSearch.updatePattern(text)
        }
      })
    }

    val panel = JBUI.Panels.simplePanel(scrollPane).addToTop(searchField)
    ScrollingUtil.installActions(list, panel)
    ListUtil.installAutoSelectOnMouseMove(list)

    fun toggleSelection() {
      for (item in list.selectedValuesList) {
        item.selected = !item.selected
      }
      list.repaint()
    }

    list.addMouseListener(object : MouseAdapter() {
      override fun mouseReleased(e: MouseEvent) {
        if (UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED) && !UIUtil.isSelectionButtonDown(e) && !e.isConsumed) toggleSelection()
      }
    })

    val originalSelection: Set<T> = currentList.toHashSet()
    listModel.add(currentList.map { SelectableWrapper(it, true) })

    val result = CompletableFuture<CollectionDelta<T>>()
    JBPopupFactory.getInstance().createComponentPopupBuilder(panel, searchField)
      .setRequestFocus(true)
      .setCancelOnClickOutside(true)
      .setTitle(popupTitle)
      .setResizable(true)
      .setMovable(true)
      .setKeyboardActions(listOf(Pair.create(ActionListener { toggleSelection() }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))))
      .addListener(object : JBPopupListener {
        override fun beforeShown(event: LightweightWindowEvent) {
          list.setPaintBusy(true)
          list.emptyText.text = ApplicationBundle.message("label.loading.page.please.wait")

          availableListFuture
            .thenApplyAsync { available ->
              available.map { SelectableWrapper(it, originalSelection.contains(it)) }
                .sortedWith(Comparator.comparing<SelectableWrapper<T>, Boolean> { !it.selected }
                              .thenComparing({ listCellRenderer.getText(it.value) }) { a, b -> StringUtil.compare(a, b, true) })
            }.successOnEdt {
              listModel.replaceAll(it)

              list.setPaintBusy(false)
              list.emptyText.text = UIBundle.message("message.noMatchesFound")

              event.asPopup().pack(true, true)

              if (list.selectedIndex == -1) {
                list.selectedIndex = 0
              }
            }
        }

        override fun onClosed(event: LightweightWindowEvent) {
          val selected = listModel.items.filter { it.selected }.map { it.value }
          result.complete(CollectionDelta(originalSelection, selected))
        }
      })
      .createPopup()
      .showUnderneathOf(parentComponent)
    return result
  }

  data class SelectableWrapper<T>(val value: T, var selected: Boolean = false)

  sealed class SelectionListCellRenderer<T> : ListCellRenderer<SelectableWrapper<T>>, BorderLayoutPanel() {

    private val mainLabel = JLabel()
    private val checkIconLabel = JLabel()

    init {
      checkIconLabel.iconTextGap = JBUI.scale(UIUtil.DEFAULT_VGAP)
      checkIconLabel.border = JBUI.Borders.empty(0, 4)

      addToLeft(checkIconLabel)
      addToCenter(mainLabel)

      border = JBUI.Borders.empty(4, 0)
    }

    override fun getListCellRendererComponent(list: JList<out SelectableWrapper<T>>,
                                              value: SelectableWrapper<T>,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      foreground = UIUtil.getListForeground(isSelected, true)
      background = UIUtil.getListBackground(isSelected, true)

      mainLabel.foreground = foreground
      mainLabel.font = font

      mainLabel.text = getText(value.value)
      mainLabel.icon = getIcon(value.value)

      val icon = LafIconLookup.getIcon("checkmark", isSelected, false)
      checkIconLabel.icon = if (value.selected) icon else EmptyIcon.create(icon)

      return this
    }

    abstract fun getText(value: T): String
    abstract fun getIcon(value: T): Icon

    class PRReviewers(private val iconsProvider: CachingGithubAvatarIconsProvider)
      : SelectionListCellRenderer<GHPullRequestRequestedReviewer>() {
      override fun getText(value: GHPullRequestRequestedReviewer) = value.shortName
      override fun getIcon(value: GHPullRequestRequestedReviewer) = iconsProvider.getIcon(value.avatarUrl)
    }

    class Users(private val iconsProvider: CachingGithubAvatarIconsProvider)
      : SelectionListCellRenderer<GHUser>() {
      override fun getText(value: GHUser) = value.login
      override fun getIcon(value: GHUser) = iconsProvider.getIcon(value.avatarUrl)
    }

    class Labels : SelectionListCellRenderer<GHLabel>() {
      override fun getText(value: GHLabel) = value.name
      override fun getIcon(value: GHLabel) = ColorIcon(16, ColorUtil.fromHex(value.color))
    }
  }
}

fun Action.getName(): String = (getValue(Action.NAME) as? String).orEmpty()