// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemView.security.backend

import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewTabWithMetrics
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewToolWindowUtils
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlChunk.body
import com.intellij.openapi.util.text.HtmlChunk.html
import com.intellij.openapi.util.text.HtmlChunk.tag
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ColorUtil
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.content.Content
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
open class SubTabbedProblemsViewTab(
  final override val project: Project,
  final override val hostTabId: String,
  @param:NlsContexts.TabTitle private val tabTitle: String,
  final override val usagesTabId: String,
) : ProblemsViewTabWithMetrics(), ProblemsViewSubTabHost, Disposable {

  private val scope: CoroutineScope =
    project.service<ProblemsViewSubTabScopeService>().scope.childScope("SubTabbedProblemsViewTab($hostTabId)")
  private val entries: List<ProblemsViewSubTab> = subTabProviders(hostTabId).mapNotNull {
    it.createSubTab(project, scope.childScope(it.javaClass.name), this)
  }
  private val selector: ProblemsViewSubTabSelector = ProblemsViewSubTabSelector(scope, entries)
  private val subTabPanel: JPanel = JPanel(BorderLayout())

  private val _shownSubTabId: MutableStateFlow<String?> = MutableStateFlow(null)

  final override val shownSubTabId: StateFlow<String?> = _shownSubTabId.asStateFlow()

  private val visible: MutableStateFlow<Boolean> = MutableStateFlow(false)

  init {
    ThreadingAssertions.assertEventDispatchThread()
    setContent(createSplitter())
    selector.select(entries.firstOrNull()?.id)
    // mount the selected sub-tab before the constructor returns
    showSubTab(selector.selectedItemId.value)
    initSelectionSubscription()
    initShownSubTabSubscription()
    initProblemCountsSubscription()
  }

  final override fun getTabId(): String = hostTabId

  final override fun getName(count: Int): String = when {
    count <= 0 -> tabTitle
    else -> {
      val margin = JBUI.scale(8)
      val counterColor = ColorUtil.toHtmlColor(UIUtil.getInactiveTextColor())
      // an HTML table gives the spacing, because the label does not support full HTML4+
      html()
        .child(body()
                 .child(tag("table").attr("cellspacing", "0").attr("cellpadding", "0")
                          .child(tag("tr")
                                   .child(tag("td").addText(tabTitle))
                                   .child(tag("td").attr("width", "${margin}px"))
                                   .child(tag("td").attr("color", counterColor).addText(count.toString()))
                          )
                 )
        ).toString()
    }
  }

  final override val shownProblemsCount: Int
    get() = entries.sumOf { it.presentation.value.problemCount ?: 0 }

  override fun customizeTabContent(content: Content) {
    content.setDisposer(this)
  }

  final override fun visibilityChangedTo(visible: Boolean) {
    super.visibilityChangedTo(visible)
    this.visible.value = visible
  }

  override fun dispose() {
    scope.cancel()
  }

  /** Ignores an unknown [subTabId]. */
  final override fun selectSubTab(@NonNls subTabId: String) {
    ThreadingAssertions.assertEventDispatchThread()
    selector.select(subTabId)
  }

  final override fun findSubTab(@NonNls subTabId: String): ProblemsViewSubTab? {
    ThreadingAssertions.assertEventDispatchThread()
    return entries.firstOrNull { it.id == subTabId }
  }

  private fun initSelectionSubscription() {
    scope.launch(Dispatchers.EDT) {
      selector.selectedItemId.collect { showSubTab(it) }
    }
  }

  private fun initShownSubTabSubscription() {
    scope.launch(Dispatchers.EDT) {
      var shownSubTab: ProblemsViewSubTab? = null
      combine(_shownSubTabId, visible) { id, isVisible ->
        val subTab = if (isVisible) entries.firstOrNull { it.id == id } else null
        subTab to isVisible
      }
        .distinctUntilChanged()
        .collect { (subTab, isVisible) ->
          shownSubTab?.selectionChangedTo(false)
          subTab?.selectionChangedTo(true)
          shownSubTab = subTab
          updateGearActions(subTab, isVisible)
        }
    }
  }

  private fun initProblemCountsSubscription() {
    if (entries.isEmpty()) return
    scope.launch(Dispatchers.EDT) {
      combine(entries.map { it.presentation }) { all -> all.sumOf { it.problemCount ?: 0 } }
        .distinctUntilChanged()
        .collect { updateTabName() }
    }
  }

  private fun showSubTab(@NonNls subTabId: String?) {
    ThreadingAssertions.assertEventDispatchThread()
    val subTab = entries.firstOrNull { it.id == subTabId }
    if (_shownSubTabId.value == subTab?.id) return
    val component = subTab?.component
    subTabPanel.removeAll()
    component?.let { subTabPanel.add(it, BorderLayout.CENTER) }
    _shownSubTabId.value = subTab?.id
    subTabPanel.revalidate()
    subTabPanel.repaint()
  }

  private fun createSplitter(): OnePixelSplitter =
    OnePixelSplitter(false, "ProblemsView.SubTabs.$hostTabId.proportion", SELECTOR_PROPORTION).apply {
      firstComponent = selector
      secondComponent = subTabPanel
    }

  private fun updateTabName() {
    val content = ProblemsViewToolWindowUtils.getContentById(project, hostTabId) ?: return
    content.displayName = getName(shownProblemsCount)
  }

  private fun updateGearActions(subTab: ProblemsViewSubTab?, isVisible: Boolean) {
    if (!isVisible) return
    val window = ProblemsView.getToolWindow(project) as? ToolWindowEx ?: return
    val group = subTab?.gearActionGroupId?.let { ActionManager.getInstance().getAction(it) as? ActionGroup }
    window.setAdditionalGearActions(group)
  }

  companion object {
    private const val SELECTOR_PROPORTION: Float = 0.2F

    fun hasSubTabs(@NonNls hostTabId: String): Boolean = subTabProviders(hostTabId).isNotEmpty()

    private fun subTabProviders(@NonNls hostTabId: String): List<ProblemsViewSubTabProvider> =
      ProblemsViewSubTabProvider.EP.extensionList.filter { it.hostTabId == hostTabId }
  }
}

@Service(Service.Level.PROJECT)
internal class ProblemsViewSubTabScopeService(@JvmField val scope: CoroutineScope)
