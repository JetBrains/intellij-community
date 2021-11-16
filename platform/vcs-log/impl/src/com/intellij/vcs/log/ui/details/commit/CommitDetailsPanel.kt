// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.details.commit

import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.ui.FontUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.ClickListener
import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.HtmlPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil.*
import com.intellij.vcs.log.ui.frame.VcsCommitExternalStatusPresentation
import com.intellij.vcs.log.util.VcsLogUiUtil
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent
import kotlin.properties.Delegates.observable

class CommitDetailsPanel @JvmOverloads constructor(navigate: (CommitId) -> Unit = {}) : JPanel() {
  companion object {
    const val SIDE_BORDER = 14
    const val INTERNAL_BORDER = 10
    const val EXTERNAL_BORDER = 14
  }

  data class RootColor(val root: VirtualFile, val color: Color)

  private val hashAndAuthorPanel = HashAndAuthorPanel()
  private val signaturePanel = SignaturePanel()
  private val messagePanel = CommitMessagePanel(navigate)
  private val branchesPanel = ReferencesPanel(Registry.intValue("vcs.log.max.branches.shown"))
  private val tagsPanel = ReferencesPanel(Registry.intValue("vcs.log.max.tags.shown"))
  private val rootPanel = RootColorPanel(hashAndAuthorPanel)
  private val containingBranchesPanel = ContainingBranchesPanel()

  init {
    layout = VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false)
    isOpaque = false

    val metadataPanel = BorderLayoutPanel().apply {
      isOpaque = false
      border = JBUI.Borders.empty(INTERNAL_BORDER, SIDE_BORDER)
      addToLeft(rootPanel)
      addToCenter(hashAndAuthorPanel)
      addToBottom(signaturePanel)
    }

    add(messagePanel)
    add(metadataPanel)
    add(branchesPanel)
    add(tagsPanel)
    add(containingBranchesPanel)
  }

  fun setCommit(presentation: CommitPresentation) {
    messagePanel.updateMessage(presentation)
    hashAndAuthorPanel.updateHashAndAuthor(presentation)
  }

  fun setRefs(references: List<VcsRef>?) {
    references ?: return
    branchesPanel.setReferences(references.filter { it.type.isBranch })
    tagsPanel.setReferences(references.filter { !it.type.isBranch })
    if (tagsPanel.isVisible) {
      branchesPanel.border = JBUI.Borders.empty(0, SIDE_BORDER - ReferencesPanel.H_GAP, 0, SIDE_BORDER)
      tagsPanel.border = JBUI.Borders.empty(0, SIDE_BORDER - ReferencesPanel.H_GAP, INTERNAL_BORDER, SIDE_BORDER)
    }
    else if (branchesPanel.isVisible) {
      branchesPanel.border = JBUI.Borders.empty(0, SIDE_BORDER - ReferencesPanel.H_GAP, INTERNAL_BORDER, SIDE_BORDER)
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
    //TODO: show the rest of the statuses
    signaturePanel.signature = statuses.find { it is VcsCommitExternalStatusPresentation.Signature }
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
    border = JBUI.Borders.empty(CommitDetailsPanel.EXTERNAL_BORDER, CommitDetailsPanel.SIDE_BORDER, CommitDetailsPanel.INTERNAL_BORDER,
      CommitDetailsPanel.SIDE_BORDER)
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

private class ContainingBranchesPanel : HtmlPanel() {
  private var branches: List<String>? = null
  private var expanded = false

  init {
    border = JBUI.Borders.empty(0, CommitDetailsPanel.SIDE_BORDER, CommitDetailsPanel.EXTERNAL_BORDER, CommitDetailsPanel.SIDE_BORDER)
    isVisible = false
  }

  override fun setBounds(x: Int, y: Int, w: Int, h: Int) {
    val oldWidth = width
    super.setBounds(x, y, w, h)
    if (w != oldWidth) {
      update()
    }
  }

  override fun hyperlinkUpdate(e: HyperlinkEvent) {
    if (e.eventType == HyperlinkEvent.EventType.ACTIVATED && isShowHideBranches(e)) {
      expanded = !expanded
      update()
    }
  }

  fun setBranches(branches: List<String>?) {
    this.branches = branches
    expanded = false
    isVisible = true

    update()
  }

  override fun getBody(): String {
    val insets = insets
    val text = getBranchesText(branches, expanded, width - insets.left - insets.right, getFontMetrics(bodyFont))
    return if (expanded) text else HtmlChunk.raw(text).wrapWith("nobr").toString()
  }

  override fun getBackground(): Color = getCommitDetailsBackground()

  override fun getBodyFont(): Font = FontUtil.getCommitMetadataFont()
}

private class HashAndAuthorPanel : HtmlPanel() {
  private var presentation: CommitPresentation? = null
  override fun getBody(): String = presentation?.hashAndAuthor ?: ""

  init {
    border = JBUI.Borders.empty()
  }

  fun updateHashAndAuthor(commitAndAuthorPresentation: CommitPresentation?) {
    presentation = commitAndAuthorPresentation
    update()
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
  private var tooltipText: String? = null
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
      icon = JBUI.scale(ColorIcon(ROOT_ICON_SIZE, rootColor.color))
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

private class SignaturePanel : BorderLayoutPanel() {
  val label = JLabel().apply {
    foreground = JBColor.GRAY
  }.also {
    object : ClickListener() {
      override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
        return (signature as? VcsCommitExternalStatusPresentation.Clickable)?.onClick(event) ?: false
      }
    }.installOn(it)
  }

  var signature: VcsCommitExternalStatusPresentation? by observable(null) { _, _, newValue ->
    isVisible = newValue != null
    label.apply {
      icon = newValue?.icon
      text = newValue?.shortDescriptionText
      toolTipText = newValue?.fullDescriptionHtml
      cursor =
        if (newValue is VcsCommitExternalStatusPresentation.Clickable) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        else Cursor.getDefaultCursor()
    }
  }

  init {
    isVisible = false
    isOpaque = false
    addToLeft(label)
  }
}

fun getCommitDetailsBackground(): Color = UIUtil.getTreeBackground()