// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.details.commit

import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.ui.FontUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.*
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.ui.RootIcon
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil.*
import com.intellij.vcs.log.ui.frame.VcsCommitExternalStatusPresentation
import com.intellij.vcs.log.util.VcsLogUiUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent

class CommitDetailsPanel @JvmOverloads constructor(navigate: (CommitId) -> Unit = {}) : JPanel() {
  companion object {
    const val SIDE_BORDER = 14
    const val INTERNAL_BORDER = 10
    const val EXTERNAL_BORDER = 14
    const val LAYOUT_MIN_WIDTH = 40
  }

  private val statusesActionGroup = DefaultActionGroup()

  data class RootColor(val root: VirtualFile, val color: Color)

  private val hashAndAuthorPanel = HashAndAuthorPanel()
  private val statusesToolbar = ActionManager.getInstance().createActionToolbar("CommitDetailsPanel", statusesActionGroup, false).apply {
    targetComponent = this@CommitDetailsPanel
    (this as ActionToolbarImpl).setForceShowFirstComponent(true)
    component.apply {
      isOpaque = false
      border = JBUI.Borders.empty()
      isVisible = false
    }
  }
  private val messagePanel = CommitMessagePanel(navigate)
  private val branchesPanel = ReferencesPanel(Registry.intValue("vcs.log.max.branches.shown"))
  private val tagsPanel = ReferencesPanel(Registry.intValue("vcs.log.max.tags.shown"))
  private val rootPanel = RootColorPanel(hashAndAuthorPanel)
  private val containingBranchesPanel = ContainingBranchesPanel()

  init {
    layout = MigLayout(LC().gridGap("0", "0").insets("0").fill())
    isOpaque = false

    val mainPanel = JPanel(null).apply {
      layout = MigLayout(LC().gridGap("0", "0").insets("0").fill().flowY())
      isOpaque = false

      val metadataPanel = BorderLayoutPanel().apply {
        border = JBUI.Borders.empty(INTERNAL_BORDER, SIDE_BORDER, INTERNAL_BORDER, 0)
        isOpaque = false
        addToLeft(rootPanel)
        addToCenter(hashAndAuthorPanel)
      }

      val componentLayout = CC().minWidth("$LAYOUT_MIN_WIDTH").grow().push()
      add(messagePanel, componentLayout)
      add(metadataPanel, componentLayout)
      add(branchesPanel, componentLayout)
      add(tagsPanel, componentLayout)
      add(containingBranchesPanel, componentLayout)
    }

    add(mainPanel, CC().grow().push())
    //show at most 4 icons
    val maxHeight = 22 * 4
    add(statusesToolbar.component, CC().hideMode(3).alignY("top").maxHeight("$maxHeight"))

    updateStatusToolbar(false)
  }

  fun setCommit(presentation: CommitPresentation) {
    messagePanel.updateMessage(presentation)
    hashAndAuthorPanel.presentation = presentation
  }

  fun setRefs(references: List<VcsRef>?) {
    references ?: return
    branchesPanel.setReferences(references.filter { it.type.isBranch })
    tagsPanel.setReferences(references.filter { !it.type.isBranch })
    if (tagsPanel.isVisible) {
      branchesPanel.border = JBUI.Borders.emptyLeft(SIDE_BORDER - ReferencesPanel.H_GAP)
      tagsPanel.border = JBUI.Borders.empty(0, SIDE_BORDER - ReferencesPanel.H_GAP, INTERNAL_BORDER, 0)
    }
    else if (branchesPanel.isVisible) {
      branchesPanel.border = JBUI.Borders.empty(0, SIDE_BORDER - ReferencesPanel.H_GAP, INTERNAL_BORDER, 0)
    }
    update()
  }

  fun setRoot(rootColor: RootColor?) {
    rootPanel.setRoot(rootColor)
  }

  fun setBranches(branches: List<String>?) {
    containingBranchesPanel.setBranches(branches)
  }

  fun setStatuses(statuses: List<VcsCommitExternalStatusPresentation>) {
    hashAndAuthorPanel.signature = statuses.filterIsInstance<VcsCommitExternalStatusPresentation.Signature>().firstOrNull()

    val nonSignaturesStatuses = statuses.filter { it !is VcsCommitExternalStatusPresentation.Signature }

    statusesActionGroup.removeAll()
    statusesActionGroup.addAll(nonSignaturesStatuses.map(::statusToAction))

    updateStatusToolbar(nonSignaturesStatuses.isNotEmpty())
  }

  private fun statusToAction(status: VcsCommitExternalStatusPresentation) =
    object : DumbAwareAction(status.text, null, status.icon) {
      override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
      }

      override fun update(e: AnActionEvent) {
        e.presentation.apply {
          isVisible = true
          isEnabled = status is VcsCommitExternalStatusPresentation.Clickable && status.clickEnabled(e.inputEvent)
          disabledIcon = status.icon
        }
      }

      override fun actionPerformed(e: AnActionEvent) {
        if (status is VcsCommitExternalStatusPresentation.Clickable) {
          if (status.clickEnabled(e.inputEvent))
            status.onClick(e.inputEvent)
        }
      }
    }

  private fun updateStatusToolbar(hasStatuses: Boolean) {
    border = if (hasStatuses) JBUI.Borders.empty() else JBUI.Borders.emptyRight(SIDE_BORDER)
    statusesToolbar.updateActionsImmediately()
    statusesToolbar.component.isVisible = hasStatuses
  }

  fun update() {
    messagePanel.update()
    rootPanel.update()
    hashAndAuthorPanel.update()
    branchesPanel.update()
    tagsPanel.update()
    containingBranchesPanel.update()
  }

  override fun getBackground(): Color = getCommitDetailsBackground()
}

private class CommitMessagePanel(private val navigate: (CommitId) -> Unit) : HtmlPanel() {
  private var presentation: CommitPresentation? = null

  override fun hyperlinkUpdate(e: HyperlinkEvent) {
    presentation?.let { presentation ->
      if (e.eventType == HyperlinkEvent.EventType.ACTIVATED && isGoToHash(e)) {
        val commitId = presentation.parseTargetCommit(e) ?: return
        navigate(commitId)
      }
      else {
        BrowserHyperlinkListener.INSTANCE.hyperlinkUpdate(e)
      }
    }
  }

  init {
    border = JBUI.Borders.empty(CommitDetailsPanel.EXTERNAL_BORDER, CommitDetailsPanel.SIDE_BORDER, CommitDetailsPanel.INTERNAL_BORDER, 0)
  }

  fun updateMessage(message: CommitPresentation?) {
    presentation = message
    update()
  }

  override fun getBody() = presentation?.text ?: ""

  override fun getBackground(): Color = getCommitDetailsBackground()

  override fun update() {
    isVisible = presentation != null
    super.update()
  }
}

private class ContainingBranchesPanel : JPanel() {
  private var branches: List<@NlsSafe String>? = null
  private var expanded = false

  private val shortLinkPanel = ShortBranchesLinkHtmlPanel()
  private val branchesTextArea = JBTextArea()

  init {
    border = JBUI.Borders.empty(0, CommitDetailsPanel.SIDE_BORDER, CommitDetailsPanel.EXTERNAL_BORDER, 0)
    isVisible = false
    isOpaque = false

    branchesTextArea.isEditable = false
    branchesTextArea.font = FontUtil.getCommitMetadataFont()
    branchesTextArea.background = getCommitDetailsBackground()

    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    add(shortLinkPanel)
    add(branchesTextArea)
  }

  fun setBranches(branches: List<@NlsSafe String>?) {
    this.branches = branches
    expanded = false
    isVisible = true

    updateBranches()
  }

  fun update() {
    updateBranches()
  }

  private fun updateBranches() {
    shortLinkPanel.update()

    val branches = branches
    if (expanded && branches != null) {
      val oldText = branchesTextArea.text
      val newText = branches.joinToString("\n")
      if (oldText != newText) { // avoid layout of huge texts without need
        branchesTextArea.text = newText
        branchesTextArea.caretPosition = 0
      }
      branchesTextArea.isVisible = true
    }
    else {
      branchesTextArea.text = ""
      branchesTextArea.isVisible = false
    }

    revalidate()
    repaint()
  }

  private inner class ShortBranchesLinkHtmlPanel : HtmlPanel() {
    override fun hyperlinkUpdate(e: HyperlinkEvent) {
      if (e.eventType == HyperlinkEvent.EventType.ACTIVATED && isShowHideBranches(e)) {
        expanded = !expanded
        updateBranches()
      }
    }

    override fun setBounds(x: Int, y: Int, w: Int, h: Int) {
      val oldWidth = width
      super.setBounds(x, y, w, h)
      if (w != oldWidth) {
        update()
      }
    }

    override fun getBody(): String {
      val insets = insets
      val availableWidth = width - insets.left - insets.right
      val text = getBranchesLinkText(branches, expanded, availableWidth, getFontMetrics(bodyFont))
      return if (expanded) text else HtmlChunk.raw(text).wrapWith("nobr").toString()
    }

    override fun getBackground(): Color = getCommitDetailsBackground()

    override fun getBodyFont(): Font = FontUtil.getCommitMetadataFont()
  }
}

private class HashAndAuthorPanel : HtmlPanel() {

  init {
    editorKit = HTMLEditorKitBuilder()
      .withViewFactoryExtensions(ExtendableHTMLViewFactory.Extensions.WORD_WRAP,
                                 ExtendableHTMLViewFactory.Extensions.icons {
                                   signature?.icon
                                 }
      )
      .build().apply {
        //language=css
        styleSheet.addRule(""".signature {
            color: ${ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground())};
        }""".trimMargin())
      }
  }

  var presentation: CommitPresentation? = null
    set(value) {
      field = value
      update()
    }

  var signature: VcsCommitExternalStatusPresentation.Signature? = null
    set(value) {
      field = value
      update()
    }

  override fun getBody(): String {
    val presentation = presentation ?: return ""
    val signature = signature

    @Suppress("HardCodedStringLiteral")
    return presentation.hashAndAuthor.let {
      if (signature != null) {
        val tooltip = signature.description?.toString()
        //language=html
        it + """<span class='signature'>&nbsp;&nbsp;&nbsp; 
          |<icon src='sig' alt='${tooltip.orEmpty()}'/>
          |&nbsp;${signature.text}
          |</span>""".trimMargin()
      }
      else it
    }
  }

  init {
    border = JBUI.Borders.empty()
  }

  public override fun getBodyFont(): Font = FontUtil.getCommitMetadataFont()

  override fun update() {
    isVisible = presentation != null
    super.update()
  }
}

private class RootColorPanel(private val parent: HashAndAuthorPanel) : Wrapper(parent) {
  companion object {
    private const val ROOT_ICON_SIZE = 13
    private const val ROOT_GAP = 4
  }

  private var icon: ColorIcon? = null
  private var tooltipText: @NlsContexts.Tooltip String? = null
  private val mouseMotionListener = object : MouseAdapter() {
    override fun mouseMoved(e: MouseEvent?) {
      if (IdeTooltipManager.getInstance().hasCurrent()) {
        IdeTooltipManager.getInstance().hideCurrent(e)
        return
      }
      icon?.let { icon ->
        tooltipText?.let { tooltipText ->
          VcsLogUiUtil.showTooltip(this@RootColorPanel, Point(icon.iconWidth / 2, 0), Balloon.Position.above, tooltipText)
        }
      }
    }
  }

  init {
    setVerticalSizeReferent(parent)
    addMouseMotionListener(mouseMotionListener)
  }

  override fun getPreferredSize(): Dimension = icon?.let { icon ->
    val size = super.getPreferredSize()
    Dimension(icon.iconWidth + JBUIScale.scale(ROOT_GAP), size.height)
  } ?: Dimension(0, 0)

  fun setRoot(rootColor: CommitDetailsPanel.RootColor?) {
    if (rootColor != null) {
      icon = RootIcon.createAndScale(rootColor.color)
      tooltipText = rootColor.root.path
    }
    else {
      icon = null
      tooltipText = null
    }
  }

  fun update() {
    isVisible = icon != null
    revalidate()
    repaint()
  }

  override fun getBackground(): Color = getCommitDetailsBackground()

  override fun paintComponent(g: Graphics) {
    icon?.let { icon ->
      val h = FontUtil.getStandardAscent(parent.bodyFont, g)
      val metrics = getFontMetrics(parent.bodyFont)
      icon.paintIcon(this, g, 0, metrics.maxAscent - h + (h - icon.iconHeight - 1) / 2)
    }
  }
}

fun getCommitDetailsBackground(): Color = UIUtil.getTreeBackground()