// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.statusMessageKey
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.providers.agentSessionThreadStatusIcon
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.visibleProjectBranch
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.ProductIcons
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleColoredComponent.FragmentTextClipper
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.Icon
import javax.swing.JTree

private const val SESSION_TREE_MIDDLE_TEXT_CACHE_LIMIT = 1024

internal class SessionTreeCellRenderer(
  private val nowProvider: () -> Long,
  private val rowActionsProvider: (row: Int, node: SessionTreeNode, selected: Boolean) -> SessionTreeRowActionPresentation?,
  private val nodeResolver: (SessionTreeId) -> SessionTreeNode?,
  private val providerIconProvider: ((AgentSessionProvider) -> Icon?)? = null,
) : ColoredTreeCellRenderer() {
  private data class SharedTimeColumnWidthCacheKey(
    @JvmField val fontHash: Int,
    @JvmField val labelsSignature: @NlsSafe String,
  )

  private data class MiddleTextCacheKey(
    @JvmField val fontHash: Int,
    @JvmField val text: @NlsSafe String,
    @JvmField val availTextWidth: Int,
    @JvmField val rightReservedWidth: Int,
  )

  private data class ProjectCompositeIconCacheKey(
    @JvmField val badgeId: String,
    @JvmField val baseIconWidth: Int,
    @JvmField val baseIconHeight: Int,
  )

  private var threadTrailingPaint: SessionTreeThreadTrailingPaint? = null
  private var sharedTimeColumnWidthCacheKey: SharedTimeColumnWidthCacheKey? = null
  private var sharedTimeColumnWidthCacheValue: Int = 0
  private val projectCompositeIconCache = LinkedHashMap<ProjectCompositeIconCacheKey, Icon>()
  private val middleTextCache = LinkedHashMap<MiddleTextCacheKey, @NlsSafe String>()
  private val middleTextClipper = SessionTreeMiddleTextClipper(::clipMiddleText)

  internal val trailingThreadPaintForTest: SessionTreeThreadTrailingPaint?
    get() = threadTrailingPaint

  override fun customizeCellRenderer(
    tree: JTree,
    value: Any?,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean,
  ) {
    threadTrailingPaint = null
    setAccessibleStatusText(null)

    val treeId = extractSessionTreeId(value) ?: return
    val treeNode = nodeResolver(treeId) ?: return
    val rowActions = rowActionsProvider(row, treeNode, selected)
    val actionRightPadding = rowActions?.reservedWidth ?: 0
    var metaRightPadding = 0

    when (treeNode) {
      is SessionTreeNode.Project -> {
        val projectIcon = projectCompositeIcon(treeNode.project)
        icon = projectIcon
        val baseFontMetrics = getFontMetrics(getBaseFont())
        val titleAttributes = if (treeNode.project.isOpen || treeNode.project.worktrees.any { it.isOpen }) {
          SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
        }
        else {
          SimpleTextAttributes.REGULAR_ATTRIBUTES
        }
        val projectName: @NlsSafe String = treeNode.project.name
        val inlineMetadataText = projectInlineMetadataText(treeNode)
        if (inlineMetadataText == null) {
          append(projectName, titleAttributes)
        }
        else {
          metaRightPadding = baseFontMetrics.stringWidth(inlineMetadataText)
          appendWithClipping(projectName, titleAttributes, middleTextClipper)
          append(inlineMetadataText, SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
        val accessibleTrailingText: @NlsSafe String? = inlineMetadataText?.trim()
        val loadingText = if (treeNode.project.isLoading) AgentSessionsBundle.message("toolwindow.loading") else null
        val accessibleStatusText: @NlsSafe String? = when {
          accessibleTrailingText != null && loadingText != null -> "$accessibleTrailingText, $loadingText"
          accessibleTrailingText != null -> accessibleTrailingText
          else -> loadingText
        }
        if (accessibleStatusText != null) {
          setAccessibleStatusText(accessibleStatusText)
        }
      }

      is SessionTreeNode.Worktree -> {
        val worktreeIcon = AllIcons.Vcs.BranchNode
        icon = worktreeIcon
        val worktreeName: @NlsSafe String = treeNode.worktree.name
        append(worktreeName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        val branchLabel: @NlsSafe String = treeNode.worktree.branch ?: AgentSessionsBundle.message("toolwindow.worktree.detached")
        append(" [$branchLabel]", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        if (treeNode.worktree.isLoading) {
          setAccessibleStatusText(AgentSessionsBundle.message("toolwindow.loading"))
        }
      }

      is SessionTreeNode.Thread -> {
        val baseFontMetrics = getFontMetrics(getBaseFont())
        val sharedTimeColumnWidth = computeSharedTimeColumnWidth(baseFontMetrics)
        val threadRowPresentation = buildSessionTreeThreadRowPresentation(treeNode = treeNode, now = nowProvider())
        icon = threadCompositeIcon(treeNode.thread.provider, treeNode.thread.activity)
        val threadTitle: @NlsSafe String = threadRowPresentation.title
        appendWithClipping(threadTitle, SimpleTextAttributes.REGULAR_ATTRIBUTES, middleTextClipper)
        threadTrailingPaint = computeSessionTreeThreadTrailingPaint(
          tree = tree,
          actionRightPadding = actionRightPadding,
          timeLabel = threadRowPresentation.timeLabel,
          statusLabel = threadRowPresentation.costLabel,
          fontMetrics = baseFontMetrics,
          sharedTimeColumnWidth = sharedTimeColumnWidth,
        )
        metaRightPadding = threadTrailingPaint?.reserveWidth ?: 0
        if (threadRowPresentation.accessibleStatusText != null) {
          setAccessibleStatusText(threadRowPresentation.accessibleStatusText)
        }

        if (threadRowPresentation.branchMismatchMessage != null) {
          append(
            "  ${threadRowPresentation.branchMismatchMessage}",
            SimpleTextAttributes.ERROR_ATTRIBUTES,
          )
        }
      }

      is SessionTreeNode.SubAgent -> {
        icon = agentSessionThreadStatusIcon(AllIcons.Nodes.Plugin, treeNode.subAgent.activity)
        val subAgentLabel: @NlsSafe String = treeNode.subAgent.name.ifBlank { treeNode.subAgent.id }
        append(subAgentLabel, SimpleTextAttributes.GRAY_ATTRIBUTES)
        setAccessibleStatusText(AgentSessionsBundle.message(treeNode.subAgent.activity.statusMessageKey()))
      }

      is SessionTreeNode.Warning -> {
        icon = AllIcons.General.Warning
        append(treeNode.message, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, SimpleTextAttributes.GRAY_ATTRIBUTES.fgColor))
      }

      is SessionTreeNode.Error -> {
        icon = AllIcons.General.Error
        append(treeNode.message, SimpleTextAttributes.ERROR_ATTRIBUTES)
      }

      is SessionTreeNode.Empty -> {
        icon = null
        append(treeNode.message, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }

      is SessionTreeNode.MoreProjects -> {
        icon = null
        append(
          AgentSessionsBundle.message("toolwindow.action.more.count", treeNode.hiddenCount),
          SimpleTextAttributes.GRAYED_ATTRIBUTES,
          SESSION_TREE_MORE_ROW_FRAGMENT_TAG,
        )
      }

      is SessionTreeNode.MoreThreads -> {
        icon = null
        val label = treeNode.hiddenCount?.let { AgentSessionsBundle.message("toolwindow.action.more.count", it) }
                    ?: AgentSessionsBundle.message("toolwindow.action.more")
        append(label, SimpleTextAttributes.GRAYED_ATTRIBUTES, SESSION_TREE_MORE_ROW_FRAGMENT_TAG)
      }
    }

    val rightPadding = actionRightPadding + metaRightPadding
    ipad = if (rightPadding > 0) JBUI.insetsRight(rightPadding) else JBUI.emptyInsets()
  }

  override fun paintComponent(g: Graphics) {
    val g2 = g as? Graphics2D ?: run {
      super.paintComponent(g)
      return
    }

    com.intellij.ide.ui.UISettings.setupAntialiasing(g2)

    val width = this.width
    val height = this.height
    if (isOpaque) {
      g2.color = background
      g2.fillRect(0, 0, width, height)
    }

    // Paint the core renderer normally (selection/background/border), then overlay trailing thread metadata.
    // Title clipping is handled by SessionTreeMiddleTextClipper using right-side reserved space.
    super.paintComponent(g2)
    paintThreadTrailingMeta(g2)
  }

  private fun paintThreadTrailingMeta(g: Graphics2D) {
    val trailing = threadTrailingPaint ?: return
    val area = computePaintArea()

    val font = getBaseFont()
    g.font = font
    val metrics = g.getFontMetrics(font)
    val baseline = area.y + getTextBaseLine(metrics, area.height)

    val horizontalLayout = computeSessionTreeThreadHorizontalLayout(
      contentWidth = width,
      actionRightPadding = trailing.actionRightPadding,
      selectionRightInset = trailing.selectionRightInset,
      timeTextWidth = trailing.timeTextWidth,
      timeColumnWidth = trailing.timeColumnWidth,
      statusTextWidth = trailing.statusTextWidth,
      statusColumnWidth = trailing.statusColumnWidth,
    )

    trailing.statusLabel?.let { statusLabel ->
      g.color = trailingTextColor()
      g.drawString(statusLabel, horizontalLayout.statusX, baseline)
    }
    g.color = trailingTextColor()
    g.drawString(trailing.timeLabel, horizontalLayout.timeX, baseline)
  }

  private fun trailingTextColor(): Color {
    if (mySelected && isFocused() && JBUI.CurrentTheme.Tree.Selection.forceFocusedSelectionForeground()) {
      return UIUtil.getTreeSelectionForeground(true)
    }
    return getActiveTextColor(SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor)
  }

  private fun clipMiddleText(
    text: @NlsSafe String,
    fontMetrics: FontMetrics,
    availTextWidth: Int,
    rightReservedWidth: Int,
  ): @NlsSafe String {
    val cacheKey = MiddleTextCacheKey(
      fontHash = fontMetrics.font.hashCode(),
      text = text,
      availTextWidth = availTextWidth,
      rightReservedWidth = rightReservedWidth,
    )
    middleTextCache[cacheKey]?.let { return it }
    val clippedText = clipSessionTreeMiddleText(
      text = text,
      fontMetrics = fontMetrics,
      availTextWidth = availTextWidth,
      rightReservedWidth = rightReservedWidth,
    )
    middleTextCache.putBounded(cacheKey, clippedText, SESSION_TREE_MIDDLE_TEXT_CACHE_LIMIT)
    return clippedText
  }

  private fun computeSharedTimeColumnWidth(fontMetrics: FontMetrics): Int {
    val nowLabel = AgentSessionsBundle.message("toolwindow.time.now")
    val unknownLabel = AgentSessionsBundle.message("toolwindow.time.unknown")
    val key = SharedTimeColumnWidthCacheKey(
      fontHash = fontMetrics.font.hashCode(),
      labelsSignature = "$nowLabel::$unknownLabel",
    )

    val cachedKey = sharedTimeColumnWidthCacheKey
    if (cachedKey == key) {
      return sharedTimeColumnWidthCacheValue
    }

    val width = computeSessionTreeSharedTimeColumnWidth(fontMetrics)

    sharedTimeColumnWidthCacheKey = key
    sharedTimeColumnWidthCacheValue = width
    return width
  }

  private fun threadCompositeIcon(provider: AgentSessionProvider, activity: AgentThreadActivity): Icon {
    return providerIconProvider?.let { agentSessionThreadStatusIcon(it(provider), activity) }
           ?: agentSessionThreadStatusIcon(provider, activity)
  }

  private fun projectCompositeIcon(project: AgentProjectSessions): Icon {
    val baseIcon = ProductIcons.getInstance().getProjectNodeIcon()
    val buildSystemBadge = project.buildSystemBadge ?: return baseIcon
    val key = ProjectCompositeIconCacheKey(
      badgeId = buildSystemBadge.id,
      baseIconWidth = baseIcon.iconWidth,
      baseIconHeight = baseIcon.iconHeight,
    )
    return projectCompositeIconCache.getOrPut(key) {
      fitProjectIconSize(icon = buildSystemBadge.icon, targetWidth = baseIcon.iconWidth, targetHeight = baseIcon.iconHeight)
    }
  }
}

private fun fitProjectIconSize(icon: Icon, targetWidth: Int, targetHeight: Int): Icon {
  val scale = minOf(targetWidth.toFloat() / icon.iconWidth, targetHeight.toFloat() / icon.iconHeight)
  val scaledIcon = if (scale == 1f) icon else IconUtil.scale(icon = icon, ancestor = null, scale = scale)
  return if (scaledIcon.iconWidth == targetWidth && scaledIcon.iconHeight == targetHeight) {
    scaledIcon
  }
  else {
    IconUtil.toSize(scaledIcon, targetWidth, targetHeight)
  }
}

internal fun projectBranchText(project: AgentProjectSessions): @NlsSafe String? {
  val branch = visibleProjectBranch(project) ?: return null
  return " [$branch]"
}

internal fun projectInlineMetadataText(node: SessionTreeNode.Project): @NlsSafe String? {
  val branchText = projectBranchText(node.project)
  val pathQualifier = node.pathQualifier
  return when {
    branchText != null && pathQualifier != null -> "$branchText  $pathQualifier"
    branchText != null -> branchText
    pathQualifier != null -> "  $pathQualifier"
    else -> null
  }
}

internal fun clipSessionTreeMiddleText(
  text: @NlsSafe String,
  fontMetrics: FontMetrics,
  availTextWidth: Int,
  rightReservedWidth: Int,
): @NlsSafe String {
  val effectiveAvailTextWidth = (availTextWidth - rightReservedWidth).coerceAtLeast(0)

  if (effectiveAvailTextWidth <= 0) {
    return StringUtil.ELLIPSIS
  }

  if (fontMetrics.stringWidth(text) <= effectiveAvailTextWidth) {
    return text
  }

  val ellipsis = StringUtil.ELLIPSIS
  if (fontMetrics.stringWidth(ellipsis) > effectiveAvailTextWidth) {
    return ellipsis
  }

  var low = 1
  var high = text.length
  var best = ellipsis
  while (low <= high) {
    val mid = (low + high) ushr 1
    val candidate = StringUtil.trimMiddle(text, mid)
    if (fontMetrics.stringWidth(candidate) <= effectiveAvailTextWidth) {
      best = candidate
      low = mid + 1
    }
    else {
      high = mid - 1
    }
  }
  return best.ifBlank { ellipsis }
}

private class SessionTreeMiddleTextClipper(
  private val clipTextProvider: (String, FontMetrics, Int, Int) -> String,
) : FragmentTextClipper {
  override fun clipText(
    component: com.intellij.ui.SimpleColoredComponent,
    g: Graphics2D,
    fragmentIndex: Int,
    text: String,
    availTextWidth: Int,
  ): String {
    // appendWithClipping gets full component width from SimpleColoredComponent; subtract right reserved
    // area so clipped row titles don't paint into inline metadata or trailing actions.
    val rightReservedWidth = component.ipad.right + component.insets.right
    val fontMetrics = component.getFontMetrics(g.font)
    return clipTextProvider(text, fontMetrics, availTextWidth, rightReservedWidth)
  }
}

private fun <K, V> LinkedHashMap<K, V>.putBounded(key: K, value: V, limit: Int) {
  put(key, value)
  while (size > limit) {
    remove(entries.iterator().next().key)
  }
}
