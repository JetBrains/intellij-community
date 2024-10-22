// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.DimensionService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.AppUIUtil
import com.intellij.ui.ScreenUtil
import com.intellij.ui.WindowMoveListener
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.frame.XFullValueEvaluator
import com.intellij.xdebugger.impl.ui.*
import com.intellij.xdebugger.ui.TextValueVisualizer
import com.intellij.xdebugger.ui.VisualizedContentTab
import org.jetbrains.annotations.ApiStatus
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.max

/**
 * Provides tools to show a text-like value that might be formatted for better readability (JSON, XML, HTML, etc.).
 */
@ApiStatus.Internal
object VisualizedTextPopupUtil {

  private const val SELECTED_TAB_KEY_PREFIX = "DEBUGGER_VISUALIZED_TEXT_SELECTED_TAB#"

  private val LOG = Logger.getInstance(VisualizedTextPopupUtil.javaClass)

  private val extensionPoint: ExtensionPointName<TextValueVisualizer> =
    ExtensionPointName.create("com.intellij.xdebugger.textValueVisualizer")

  fun showValuePopup(event: MouseEvent, project: Project, editor: Editor?, component: JComponent, cancelCallback: Runnable?): JBPopup {
    var size = DimensionService.getInstance().getSize(DebuggerUIUtil.FULL_VALUE_POPUP_DIMENSION_KEY, project)
    if (size == null) {
      val frameSize = WindowManager.getInstance().getFrame(project)!!.size
      size = Dimension(frameSize.width / 2, frameSize.height / 2)
    }

    component.preferredSize = size

    val popup = DebuggerUIUtil.createValuePopup(project, component, cancelCallback)
    if (editor == null) {
      val bounds = Rectangle(event.locationOnScreen, size)
      ScreenUtil.fitToScreenVertical(bounds, 5, 5, true)
      if (size.width != bounds.width || size.height != bounds.height) {
        size = bounds.size
        component.preferredSize = size
      }
      popup.showInScreenCoordinates(event.component, bounds.location)
    }
    else {
      popup.showInBestPositionFor(editor)
    }
    return popup
  }

  @JvmStatic
  fun evaluateAndShowValuePopup(evaluator: XFullValueEvaluator, event: MouseEvent, project: Project, editor: Editor?) {
    if (evaluator is CustomComponentEvaluator) {
      return evaluator.show(event, project, editor)
    }

    val panel = VisualizedTextPanel(project)
    val callback = EvaluationCallback(panel)
    val popup = showValuePopup(event, project, editor, panel, callback::setObsolete)
    Disposer.register(popup, panel)
    evaluator.startEvaluation(callback) // to make it really cancellable
  }

  class VisualizedTextPanel(private val project: Project) : JPanel(CardLayout()), Disposable.Default  {
    sealed class State
    data class Showing(val text: String) : State()
    data class Editing(val initText: String, val editor: Editor) : State()
    object Other : State()

    private var state: State = Other

    init {
      showTextMessage(XDebuggerUIConstants.getEvaluatingExpressionMessage())
    }

    private fun showComponent(component: JComponent) {
      removeAll()
      add(component)
      preferredSize = component.preferredSize
      revalidate()
      repaint()
    }

    fun showTextMessage(value: String, format: (TextViewer) -> Unit = {}) {
      val textArea = DebuggerUIUtil.createTextViewer(value, project)
      format(textArea)
      textArea.preferredSize = JBUI.size(300, 60)
      showComponent(textArea)
      state = Other
    }

    fun showError(errorMessage: String) {
      showTextMessage("ERROR OCCURRED: $errorMessage") {
        it.foreground = XDebuggerUIConstants.ERROR_MESSAGE_ATTRIBUTES.fgColor
      }
    }

    /** Visualize the text and show it nicely. */
    fun showVisualizedText(value: String) {
      val tabs = collectVisualizedTabs(project, value, parentDisposable = this)
      assert(tabs.isNotEmpty()) { "at least one raw tab is expected to be provided by fallback visualizer" }
      val component = if (tabs.size > 1) {
        createTabbedPane(tabs)
      } else {
        val (tab, component) = tabs.first()
        tab.onShown(project, firstTime = true)
        component
      }
      showComponent(component)
      state = Showing(value)
    }

    private fun createTabbedPane(tabsAndComponents: List<Pair<VisualizedContentTab, JComponent>>): JComponent {
      assert(tabsAndComponents.isNotEmpty())

      val panel = JBTabbedPane()
      panel.tabComponentInsets = JBUI.emptyInsets()
      WindowMoveListener(panel).installTo(panel)

      for ((tab, component) in tabsAndComponents) {
        panel.addTab(tab.name, component)
      }

      val tabs = tabsAndComponents.map { it.first }

      val tabIds = tabs.map { it.id }
      if (tabIds.distinct().size != tabIds.size) {
        LOG.error("non-unique tab ids: $tabIds")
      }

      // We try to make it content-specific by remembering separate value for every set of tabs.
      // E.g., it allows remembering that in the group HTML+XML+RAW user prefers HTML, and in the group HTML+MARKDOWN+RAW -- MARKDOWN.
      val selectedTabKey = SELECTED_TAB_KEY_PREFIX + tabIds.sorted().joinToString("#")

      val alreadyShownTabs = mutableSetOf<VisualizedContentTab>()
      fun onTabShown() {
        val selectedTab = tabs.getOrNull(panel.selectedIndex) ?: return
        PropertiesComponent.getInstance().setValue(selectedTabKey, selectedTab.id)

        val firstTime = alreadyShownTabs.add(selectedTab)
        selectedTab.onShown(project, firstTime)
      }

      val savedSelectedTabId = PropertiesComponent.getInstance().getValue(selectedTabKey)
      val selectedIndex = max(0, tabs.indexOfFirst { it.id == savedSelectedTabId })
      panel.selectedIndex = selectedIndex
      onTabShown() // call it manually, because change listener is triggered only if selectedIndex > 0

      val contentSize = tabsAndComponents[selectedIndex].second.preferredSize
      val tabHeight = panel.getBoundsAt(0).height
      panel.preferredSize = JBUI.size(contentSize.width, contentSize.height + tabHeight)

      panel.model.addChangeListener { onTabShown() }

      return panel
    }

    /** Start editing previously visualized text and return `true`. If there was no text shown, return `false`. */
    fun tryEditText(): Boolean {
      val initText = (state as? Showing)?.text ?: return false

      val fileType = guessTextFileType(initText)
      val parentDisposable = this
      val editor = DebuggerUIUtil.createFormattedTextEditor(initText, fileType, project, parentDisposable, false)
        .apply { component.border = JBUI.Borders.empty() }

      showComponent(editor.component)

      editor.getCaretModel().getPrimaryCaret().setSelection(0, editor.document.textLength, false)
      editor.contentComponent.requestFocus()

      state = Editing(initText, editor)
      return true
    }

    /** Finish editing and rather apply the changes and revisualize or revert to the state before editing. */
    fun finishEdit(saveChanges: Boolean): String {
      val state = (state as? Editing) ?: run {
        LOG.error("tryEditText() must be called before finishEdit()")
        return ""
      }
      val newValue = if (saveChanges) state.editor.document.text else state.initText
      showVisualizedText(newValue)
      return newValue
    }

  }

  private class EvaluationCallback(private val panel: VisualizedTextPanel) : XFullValueEvaluator.XFullValueEvaluationCallback {
    private val obsolete = AtomicBoolean(false)

    private var lastFullValueHashCode = AtomicReference<Int?>()

    override fun evaluated(fullValue: String, font: Font?) {
      // This code is not expected to be called multiple times (e.g., statistics are expected to be collected only once),
      // but it is actually called in the case of huge Java string.
      // 1. NodeDescriptorImpl.updateRepresentation() calls ValueDescriptorImpl.calcRepresentation() and it calls labelChanged()
      // 2. NodeDescriptorImpl.updateRepresentation() also directly calls labelChanged()
      // Double visualization spoils statistics and wastes the resources.
      // Try to prevent it by a simple hash code check.
      val hashCode = fullValue.hashCode()
      if (hashCode == lastFullValueHashCode.get()) return
      lastFullValueHashCode.set(hashCode)

      AppUIUtil.invokeOnEdt {
        try {
          panel.showVisualizedText(fullValue)
        }
        catch (e: Exception) {
          LOG.error(e)
          errorOccurred(e.toString())
        }
      }
    }

    override fun errorOccurred(errorMessage: String) {
      AppUIUtil.invokeOnEdt {
        panel.showError(errorMessage)
      }
    }

    fun setObsolete() {
      obsolete.set(true)
    }

    override fun isObsolete(): Boolean {
      return obsolete.get()
    }
  }

  // We return pairs because it's easier to do all dangerous stuff and catch all errors in one place.
  fun collectVisualizedTabs(project: Project, fullValue: String, parentDisposable: Disposable): List<Pair<VisualizedContentTab, JComponent>> {
    val tabs = calcNonTrivialVisualizedTabs(fullValue) +
               // Explicitly add the fallback raw visualizer to make it the last one.
               RawTextVisualizer.visualize(fullValue)

    return tabs.mapNotNull { tab ->
      safeVisualize(fullValue, "create visualized component (${tab.id})") {
        tab to tab.createComponent(project, parentDisposable)
      }
    }
  }

  @JvmStatic
  fun isVisualizable(fullValue: String): Boolean =
    // text with line breaks would be nicely rendered by the raw visualizer
    StringUtil.containsLineBreak(fullValue) ||
      calcNonTrivialVisualizedTabs(fullValue).isNotEmpty()

  private fun guessTextFileType(fullValue: String): FileType =
    extensionPoint.extensionList
      .firstNotNullOfOrNull { viz ->
        safeVisualize(fullValue, "detect file type of value ($viz)") {
          viz.detectFileType(fullValue)
        }
      }
    ?: FileTypes.PLAIN_TEXT

  private fun calcNonTrivialVisualizedTabs(fullValue: String): List<VisualizedContentTab> =
    extensionPoint.extensionList
      .flatMap { viz ->
        safeVisualize(fullValue, "visualize value ($viz)") {
          viz.visualize(fullValue)
        } ?: emptyList()
      }

  private fun <R> safeVisualize(fullValue: String, errorMsg: String, action: () -> R): R? {
    try {
      return action()
    }
    catch (t: Throwable) {
      LOG.error("failed to $errorMsg", t, Attachment("value.txt", fullValue))
      return null
    }
  }

}