// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.withAgentThreadActivityBadge
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.ui.SESSION_TREE_MORE_ROW_FRAGMENT_TAG
import com.intellij.agent.workbench.sessions.ui.SessionTreeCellRenderer
import com.intellij.agent.workbench.sessions.ui.SessionTreeRowActionPresentation
import com.intellij.agent.workbench.sessions.ui.buildSessionTreeThreadRowPresentation
import com.intellij.agent.workbench.sessions.ui.buildSessionTreeThreadTooltipHtml
import com.intellij.agent.workbench.sessions.ui.clipSessionTreeMiddleText
import com.intellij.agent.workbench.sessions.ui.computeSessionTreeThreadTrailingPaint
import com.intellij.agent.workbench.sessions.ui.configureSessionTreeRenderingProperties
import com.intellij.agent.workbench.sessions.ui.extractSessionTreeId
import com.intellij.agent.workbench.sessions.ui.isSessionTreeRowClipped
import com.intellij.agent.workbench.sessions.ui.projectBranchText
import com.intellij.agent.workbench.sessions.ui.resolveSessionTreeThreadTimePaintX
import com.intellij.agent.workbench.sessions.ui.resolveSessionTreeThreadTooltipWidth
import com.intellij.agent.workbench.sessions.ui.sessionTreeRowActionRightPadding
import com.intellij.agent.workbench.sessions.ui.sessionTreeRowActionsRightBoundary
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.ProductIcons
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.IconManager
import com.intellij.ui.render.RenderingHelper
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.IconUtil
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Rectangle
import javax.swing.JTree
import javax.swing.tree.TreePath

@TestApplication
class AgentSessionsSwingTreeCellRendererTest {
  @BeforeEach
  fun setUp() {
    IconManager.activate(null)
  }

  @AfterEach
  fun tearDown() {
    IconManager.deactivate()
  }

  @Test
  fun treeRenderingPropertiesEnableShrinkAndDisableExpandableItems() {
    val tree = Tree()

    configureSessionTreeRenderingProperties(tree)

    assertThat(tree.expandableItemsHandler.isEnabled).isFalse()
    assertThat(tree.getClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED)).isEqualTo(true)
    assertThat(tree.getClientProperty(RenderingHelper.SHRINK_LONG_RENDERER)).isEqualTo(true)
    assertThat(tree.getClientProperty(RenderingHelper.SHRINK_LONG_SELECTION)).isEqualTo(true)
  }

  @Test
  fun rowClippingDetectedWhenPathExtendsBeyondVisibleRightBoundary() {
    val clipped = isSessionTreeRowClipped(
      pathBoundsX = 20,
      pathBoundsWidth = 320,
      helperX = 0,
      helperWidth = 300,
      helperRightMargin = 8,
      selectionRightInset = 12,
    )

    assertThat(clipped).isTrue()
  }

  @Test
  fun rowClippingNotDetectedWhenPathFitsVisibleArea() {
    val clipped = isSessionTreeRowClipped(
      pathBoundsX = 20,
      pathBoundsWidth = 240,
      helperX = 0,
      helperWidth = 300,
      helperRightMargin = 8,
      selectionRightInset = 12,
    )

    assertThat(clipped).isFalse()
  }

  @Test
  fun tooltipWidthRespectsTreeVisibleWidthAndInsets() {
    val width = resolveSessionTreeThreadTooltipWidth(
      helperWidth = 300,
      helperRightMargin = 8,
      selectionRightInset = 12,
    )

    assertThat(width).isEqualTo(300 - 8 - 12 - JBUI.scale(16))
  }

  @Test
  fun tooltipWidthReturnsNullWhenNoSpaceAvailable() {
    val width = resolveSessionTreeThreadTooltipWidth(
      helperWidth = 12,
      helperRightMargin = 8,
      selectionRightInset = 12,
    )

    assertThat(width).isNull()
  }

  @Test
  fun middleClipperReservesTrailingBranchWidthForStandaloneDuplicateProjectPaths() {
    val tree = createTree(width = 420)
    val metrics = tree.getFontMetrics(tree.font)
    val projectPath = "/home/haze/work/ultimate-2/toolbox"
    val branchSuffix = " [feature-x]"
    val availableWidth = metrics.stringWidth("/home/haze/work/ultimate-2/${StringUtil.ELLIPSIS} [feature-x]")

    val clipped = clipSessionTreeMiddleText(
      text = projectPath,
      fontMetrics = metrics,
      availTextWidth = availableWidth,
      rightReservedWidth = metrics.stringWidth(branchSuffix),
    )

    assertThat(clipped).isNotEqualTo(projectPath)
    assertThat(clipped).contains(StringUtil.ELLIPSIS)
    assertThat(metrics.stringWidth(clipped) + metrics.stringWidth(branchSuffix)).isLessThanOrEqualTo(availableWidth)
  }

  @Test
  fun projectRowsUseProjectNodeIcon() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val projectId = SessionTreeId.Project(project.path)
    val renderer = SessionTreeCellRenderer(
      nowProvider = { 0L },
      rowActionsProvider = { _, _, _ -> null },
      nodeResolver = { id ->
        if (id == projectId) SessionTreeNode.Project(project) else null
      },
    )
    val tree = createTree(width = 420)

    renderer.getTreeCellRendererComponent(tree, descriptorValue(projectId), false, false, false, 0, false)

    assertThat(renderer.icon).isEqualTo(ProductIcons.getInstance().getProjectNodeIcon())
  }

  @Test
  fun projectRowsDoNotShowBranchLabelWhenWorktreesExist() {
    val project = AgentProjectSessions(
      path = "/work/project-a",
      name = "Project A",
      branch = "feature-base",
      isOpen = true,
      worktrees = listOf(
        AgentWorktree(
          path = "/work/project-feature",
          name = "project-feature",
          branch = "feature-x",
          isOpen = false,
        )
      ),
    )
    val projectId = SessionTreeId.Project(project.path)
    val renderer = SessionTreeCellRenderer(
      nowProvider = { 0L },
      rowActionsProvider = { _, _, _ -> null },
      nodeResolver = { id ->
        if (id == projectId) SessionTreeNode.Project(project) else null
      },
    )
    val tree = createTree(width = 420)

    renderer.getTreeCellRendererComponent(tree, descriptorValue(projectId), false, false, false, 0, false)

    assertThat(projectBranchText(project)).isNull()
    assertThat(renderer.getCharSequence(true).toString()).isEqualTo("Project A")
    assertThat(renderer.ipad.right).isEqualTo(0)
  }

  @Test
  fun projectRowsDoNotShowDefaultMainBranchLabelWithoutWorktrees() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", branch = "main", isOpen = true)
    val projectId = SessionTreeId.Project(project.path)
    val renderer = SessionTreeCellRenderer(
      nowProvider = { 0L },
      rowActionsProvider = { _, _, _ -> null },
      nodeResolver = { id ->
        if (id == projectId) SessionTreeNode.Project(project) else null
      },
    )
    val tree = createTree(width = 420)

    renderer.getTreeCellRendererComponent(tree, descriptorValue(projectId), false, false, false, 0, false)

    assertThat(projectBranchText(project)).isNull()
    assertThat(renderer.getCharSequence(true).toString()).isEqualTo("Project A")
    assertThat(renderer.ipad.right).isEqualTo(0)
  }

  @Test
  fun projectRowsDoNotShowDefaultMasterBranchLabelWithoutWorktrees() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", branch = "master", isOpen = true)
    val projectId = SessionTreeId.Project(project.path)
    val renderer = SessionTreeCellRenderer(
      nowProvider = { 0L },
      rowActionsProvider = { _, _, _ -> null },
      nodeResolver = { id ->
        if (id == projectId) SessionTreeNode.Project(project) else null
      },
    )
    val tree = createTree(width = 420)

    renderer.getTreeCellRendererComponent(tree, descriptorValue(projectId), false, false, false, 0, false)

    assertThat(projectBranchText(project)).isNull()
    assertThat(renderer.getCharSequence(true).toString()).isEqualTo("Project A")
    assertThat(renderer.ipad.right).isEqualTo(0)
  }

  @Test
  fun projectRowsShowNonDefaultStandaloneBranchLabel() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", branch = "feature-x", isOpen = true)
    val projectId = SessionTreeId.Project(project.path)
    val renderer = SessionTreeCellRenderer(
      nowProvider = { 0L },
      rowActionsProvider = { _, _, _ -> null },
      nodeResolver = { id ->
        if (id == projectId) SessionTreeNode.Project(project) else null
      },
    )
    val tree = createTree(width = 420)

    renderer.getTreeCellRendererComponent(tree, descriptorValue(projectId), false, false, false, 0, false)

    assertThat(projectBranchText(project)).isEqualTo(" [feature-x]")
    assertThat(renderer.ipad.right).isGreaterThan(0)
  }

  @Test
  fun duplicateProjectRowsUsePathLabelAndStillShowStandaloneBranch() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", branch = "feature-x", isOpen = true)
    val projectId = SessionTreeId.Project(project.path)
    val renderer = SessionTreeCellRenderer(
      nowProvider = { 0L },
      rowActionsProvider = { _, _, _ -> null },
      nodeResolver = { id ->
        if (id == projectId) SessionTreeNode.Project(project) else null
      },
      duplicateProjectNamesProvider = { setOf("Project A") },
    )
    val tree = createTree(width = 420)

    renderer.getTreeCellRendererComponent(tree, descriptorValue(projectId), false, false, false, 0, false)

    assertThat(projectBranchText(project)).isEqualTo(" [feature-x]")
    assertThat(renderer.getCharSequence(true).toString()).isEqualTo("/work/project-a")
    assertThat(renderer.ipad.right).isGreaterThan(0)
  }

  @Test
  fun worktreeRowsUseBranchNodeIcon() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val worktree = AgentWorktree(path = "/work/project-a-feature", name = "project-a-feature", branch = "feature", isOpen = false)
    val worktreeId = SessionTreeId.Worktree(project.path, worktree.path)
    val renderer = SessionTreeCellRenderer(
      nowProvider = { 0L },
      rowActionsProvider = { _, _, _ -> null },
      nodeResolver = { id ->
        if (id == worktreeId) SessionTreeNode.Worktree(project, worktree) else null
      },
    )
    val tree = createTree(width = 420)

    renderer.getTreeCellRendererComponent(tree, descriptorValue(worktreeId), false, false, false, 0, false)

    assertThat(renderer.icon).isEqualTo(AllIcons.Vcs.BranchNode)
  }

  @Test
  fun loadingProjectRowsUseProjectIconAndNoLoadingText() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true, isLoading = true)
    val projectId = SessionTreeId.Project(project.path)
    val renderer = SessionTreeCellRenderer(
      nowProvider = { 0L },
      rowActionsProvider = { _, _, _ -> null },
      nodeResolver = { id ->
        if (id == projectId) SessionTreeNode.Project(project) else null
      },
    )
    val tree = createTree(width = 420)

    renderer.getTreeCellRendererComponent(tree, descriptorValue(projectId), false, false, false, 0, false)

    assertThat(renderer.getCharSequence(true).toString()).doesNotContain(AgentSessionsBundle.message("toolwindow.loading"))
    assertThat(renderer.icon).isEqualTo(ProductIcons.getInstance().getProjectNodeIcon())
    assertThat(renderer.ipad.right).isEqualTo(0)
    assertThat(renderer.accessibleContext.accessibleName).contains(AgentSessionsBundle.message("toolwindow.loading"))
  }

  @Test
  fun loadingWorktreeRowsUseBranchIconAndReserveRightActionSpace() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val worktree = AgentWorktree(
      path = "/work/project-a-feature",
      name = "project-a-feature",
      branch = "feature",
      isOpen = false,
      isLoading = true,
    )
    val worktreeId = SessionTreeId.Worktree(project.path, worktree.path)
    val renderer = SessionTreeCellRenderer(
      nowProvider = { 0L },
      rowActionsProvider = { _, _, _ ->
        SessionTreeRowActionPresentation(
          showLoadingAction = true,
          quickIcon = AllIcons.General.Add,
          showQuickAction = true,
          showPopupAction = true,
          hoveredKind = null,
        )
      },
      nodeResolver = { id ->
        if (id == worktreeId) SessionTreeNode.Worktree(project, worktree) else null
      },
    )
    val tree = createTree(width = 420)

    renderer.getTreeCellRendererComponent(tree, descriptorValue(worktreeId), false, false, false, 0, false)

    assertThat(renderer.getCharSequence(true).toString()).doesNotContain(AgentSessionsBundle.message("toolwindow.loading"))
    assertThat(renderer.icon).isEqualTo(AllIcons.Vcs.BranchNode)
    assertThat(renderer.ipad.right).isEqualTo(sessionTreeRowActionRightPadding(actionSlots = 3))
    assertThat(renderer.accessibleContext.accessibleName).contains(AgentSessionsBundle.message("toolwindow.loading"))
  }

  @Test
  fun rowActionsRightBoundaryRespectsSelectionRightInset() {
    val withoutInset = sessionTreeRowActionsRightBoundary(
      helperWidth = 300,
      helperRightMargin = 8,
      rightGap = 4,
      selectionRightInset = 0,
    )
    val withInset = sessionTreeRowActionsRightBoundary(
      helperWidth = 300,
      helperRightMargin = 8,
      rightGap = 4,
      selectionRightInset = 12,
    )

    assertThat(withInset).isEqualTo(withoutInset - 12)
  }

  @Test
  fun moreProjectRowsUseMutedTextWithoutLeadingIcon() {
    val moreId = SessionTreeId.MoreProjects
    val hiddenCount = 44
    val renderer = SessionTreeCellRenderer(
      nowProvider = { 0L },
      rowActionsProvider = { _, _, _ -> null },
      nodeResolver = { id ->
        if (id == moreId) SessionTreeNode.MoreProjects(hiddenCount = hiddenCount) else null
      },
    )
    val tree = createTree(width = 420)

    renderer.getTreeCellRendererComponent(tree, descriptorValue(moreId), false, false, true, 0, false)

    assertThat(renderer.icon).isNull()
    assertThat(renderer.getCharSequence(true).toString())
      .isEqualTo(AgentSessionsBundle.message("toolwindow.action.more.count", hiddenCount))
    assertThat(renderer.getFragmentTag(0)).isEqualTo(SESSION_TREE_MORE_ROW_FRAGMENT_TAG)
  }

  @Test
  fun moreThreadRowsWithExactCountUseMutedTextWithoutLeadingIcon() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val moreId = SessionTreeId.MoreThreads(project.path)
    val hiddenCount = 4
    val renderer = SessionTreeCellRenderer(
      nowProvider = { 0L },
      rowActionsProvider = { _, _, _ -> null },
      nodeResolver = { id ->
        if (id == moreId) SessionTreeNode.MoreThreads(project = project, hiddenCount = hiddenCount) else null
      },
    )
    val tree = createTree(width = 420)

    renderer.getTreeCellRendererComponent(tree, descriptorValue(moreId), false, false, true, 0, false)

    assertThat(renderer.icon).isNull()
    assertThat(renderer.getCharSequence(true).toString())
      .isEqualTo(AgentSessionsBundle.message("toolwindow.action.more.count", hiddenCount))
    assertThat(renderer.getFragmentTag(0)).isEqualTo(SESSION_TREE_MORE_ROW_FRAGMENT_TAG)
  }

  @Test
  fun moreThreadRowsWithUnknownCountUseMutedEllipsisTextWithoutLeadingIcon() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val moreId = SessionTreeId.MoreThreads(project.path)
    val renderer = SessionTreeCellRenderer(
      nowProvider = { 0L },
      rowActionsProvider = { _, _, _ -> null },
      nodeResolver = { id ->
        if (id == moreId) SessionTreeNode.MoreThreads(project = project, hiddenCount = null) else null
      },
    )
    val tree = createTree(width = 420)

    renderer.getTreeCellRendererComponent(tree, descriptorValue(moreId), false, false, true, 0, false)

    assertThat(renderer.icon).isNull()
    assertThat(renderer.getCharSequence(true).toString())
      .isEqualTo(AgentSessionsBundle.message("toolwindow.action.more"))
    assertThat(renderer.getFragmentTag(0)).isEqualTo(SESSION_TREE_MORE_ROW_FRAGMENT_TAG)
  }

  @Test
  fun threadRowsBadgeProviderIconForNonReadyActivityAndDoNotRenderGlyphPrefix() {
    val now = 14L * 24L * 60L * 60L * 1000L
    val tree = createTree(width = 420)
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val thread = AgentSessionThread(
      provider = AgentSessionProvider.CODEX,
      id = "thread-1",
      title = "How much time",
      updatedAt = 0L,
      archived = false,
      activity = AgentThreadActivity.UNREAD,
    )
    val providerBaseIcon = EmptyIcon.create(12, 12)
    val threadId = SessionTreeId.Thread(project.path, thread.provider, thread.id)
    val renderer = SessionTreeCellRenderer(
      nowProvider = { now },
      rowActionsProvider = { _, _, _ -> null },
      nodeResolver = { id ->
        if (id == threadId) SessionTreeNode.Thread(project, thread) else null
      },
      providerIconProvider = { providerBaseIcon },
    )

    renderer.getTreeCellRendererComponent(tree, descriptorValue(threadId), false, false, true, 0, false)

    val renderedIcon = renderer.icon
    assertThat(renderedIcon).isNotNull()
    renderedIcon ?: return

    assertThat(renderedIcon).isNotSameAs(providerBaseIcon)
    assertThat(renderedIcon.iconWidth).isEqualTo(providerBaseIcon.iconWidth)
    assertThat(renderedIcon.iconHeight).isEqualTo(providerBaseIcon.iconHeight)
    assertThat(renderedIcon).isNotSameAs(AllIcons.Toolwindows.ToolWindowMessages)
    assertThat(renderer.getCharSequence(true).toString()).doesNotContain("\u25CF")
  }

  @Test
  fun threadTrailingMetadataOmitsInlineStatusForNonReadyThread() {
    val now = 28L * 24L * 60L * 60L * 1000L
    val tree = createTree(width = 460)
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val thread = AgentSessionThread(
      provider = AgentSessionProvider.CODEX,
      id = "thread-1",
      title = "Need input",
      updatedAt = 14L * 24L * 60L * 60L * 1000L,
      archived = false,
      activity = AgentThreadActivity.UNREAD,
    )
    val threadId = SessionTreeId.Thread(project.path, thread.provider, thread.id)
    val renderer = SessionTreeCellRenderer(
      nowProvider = { now },
      rowActionsProvider = { _, _, _ -> null },
      nodeResolver = { id ->
        if (id == threadId) SessionTreeNode.Thread(project, thread) else null
      },
      providerIconProvider = { EmptyIcon.create(12, 12) },
    )

    renderer.getTreeCellRendererComponent(tree, descriptorValue(threadId), false, false, true, 0, false)

    val trailing = renderer.trailingThreadPaintForTest
    assertThat(trailing).isNotNull()
    trailing ?: return

    assertThat(trailing.statusLabel).isNull()
    assertThat(trailing.statusTextWidth).isEqualTo(0)
    assertThat(trailing.statusColumnWidth).isEqualTo(0)
    assertThat(renderer.accessibleContext.accessibleName).contains(AgentSessionsBundle.message("toolwindow.thread.status.needs.input"))
  }

  @Test
  fun threadRowsBadgeProviderIconForAllActivities() {
    val now = 14L * 24L * 60L * 60L * 1000L
    val tree = createTree(width = 420)
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val readyThread = AgentSessionThread(
      provider = AgentSessionProvider.CODEX,
      id = "thread-ready",
      title = "How much time",
      updatedAt = 0L,
      archived = false,
      activity = AgentThreadActivity.READY,
    )
    val providerBaseIcon = AllIcons.Toolwindows.ToolWindowMessages
    val readyThreadId = SessionTreeId.Thread(project.path, readyThread.provider, readyThread.id)
    val renderer = SessionTreeCellRenderer(
      nowProvider = { now },
      rowActionsProvider = { _, _, _ -> null },
      nodeResolver = { id ->
        when (id) {
          readyThreadId -> SessionTreeNode.Thread(project, readyThread)
          SessionTreeId.Thread(project.path, AgentSessionProvider.CODEX, "thread-processing") -> SessionTreeNode.Thread(
            project,
            readyThread.copy(id = "thread-processing", activity = AgentThreadActivity.PROCESSING),
          )
          SessionTreeId.Thread(project.path, AgentSessionProvider.CODEX, "thread-reviewing") -> SessionTreeNode.Thread(
            project,
            readyThread.copy(id = "thread-reviewing", activity = AgentThreadActivity.REVIEWING),
          )
          SessionTreeId.Thread(project.path, AgentSessionProvider.CODEX, "thread-unread") -> SessionTreeNode.Thread(
            project,
            readyThread.copy(id = "thread-unread", activity = AgentThreadActivity.UNREAD),
          )
          else -> null
        }
      },
      providerIconProvider = { providerBaseIcon },
    )

    renderer.getTreeCellRendererComponent(tree, descriptorValue(readyThreadId), false, false, true, 0, false)
    val readyIcon = renderer.icon
    assertThat(readyIcon).isNotNull()
    readyIcon ?: return
    assertThat(readyIcon).isNotEqualTo(providerBaseIcon)

    renderer.getTreeCellRendererComponent(
      tree,
      descriptorValue(SessionTreeId.Thread(project.path, AgentSessionProvider.CODEX, "thread-processing")),
      false,
      false,
      true,
      0,
      false,
    )
    assertThat(renderer.icon).isNotSameAs(readyIcon)

    renderer.getTreeCellRendererComponent(
      tree,
      descriptorValue(SessionTreeId.Thread(project.path, AgentSessionProvider.CODEX, "thread-reviewing")),
      false,
      false,
      true,
      0,
      false,
    )
    assertThat(renderer.icon).isNotSameAs(readyIcon)

    renderer.getTreeCellRendererComponent(
      tree,
      descriptorValue(SessionTreeId.Thread(project.path, AgentSessionProvider.CODEX, "thread-unread")),
      false,
      false,
      true,
      0,
      false,
    )
    assertThat(renderer.icon).isNotNull()
    assertThat(renderer.icon).isNotSameAs(readyIcon)
  }

  @Test
  fun threadRowsBadgeFallbackWhenProviderIconIsMissing() {
    val now = 14L * 24L * 60L * 60L * 1000L
    val tree = createTree(width = 420)
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val thread = AgentSessionThread(
      provider = AgentSessionProvider.CODEX,
      id = "thread-1",
      title = "How much time",
      updatedAt = 0L,
      archived = false,
    )
    val threadId = SessionTreeId.Thread(project.path, thread.provider, thread.id)
    val renderer = SessionTreeCellRenderer(
      nowProvider = { now },
      rowActionsProvider = { _, _, _ -> null },
      nodeResolver = { id ->
        if (id == threadId) SessionTreeNode.Thread(project, thread) else null
      },
      providerIconProvider = { null },
    )

    renderer.getTreeCellRendererComponent(tree, descriptorValue(threadId), false, false, true, 0, false)

    val renderedIcon = renderer.icon
    assertThat(renderedIcon).isNotNull()
    renderedIcon ?: return

    val expectedFallback = withAgentThreadActivityBadge(
      IconUtil.toSize(AllIcons.Toolwindows.ToolWindowMessages, JBUI.scale(12), JBUI.scale(12)),
      AgentThreadActivity.READY,
    )
    assertThat(renderedIcon.javaClass).isEqualTo(expectedFallback.javaClass)
    assertThat(renderer.getCharSequence(true).toString()).doesNotContain("\u25CF")
  }

  @Test
  fun threadTrailingMetadataReservesSharedColumnAndRightAlignsTime() {
    val now = 28L * 24L * 60L * 60L * 1000L
    val tree = createTree(width = 460)
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val thread = AgentSessionThread(
      provider = AgentSessionProvider.CODEX,
      id = "thread-1",
      title = "How much time need to compute A-C?",
      updatedAt = 14L * 24L * 60L * 60L * 1000L,
      archived = false,
    )
    val threadId = SessionTreeId.Thread(project.path, thread.provider, thread.id)
    val renderer = SessionTreeCellRenderer(
      nowProvider = { now },
      rowActionsProvider = { _, _, _ -> null },
      nodeResolver = { id ->
        if (id == threadId) SessionTreeNode.Thread(project, thread) else null
      },
      providerIconProvider = { EmptyIcon.create(12, 12) },
    )

    renderer.getTreeCellRendererComponent(tree, descriptorValue(threadId), false, false, true, 0, false)

    val trailing = renderer.trailingThreadPaintForTest
    assertThat(trailing).isNotNull()
    trailing ?: return

    assertThat(trailing.timeLabel).isEqualTo("2w")
    assertThat(trailing.timeX + trailing.timeTextWidth).isEqualTo(trailing.timeRightBoundary)
    assertThat(trailing.reserveWidth).isGreaterThan(0)
  }

  @Test
  fun threadTooltipContainsFullTitleAndUpdatedTime() {
    val now = 28L * 24L * 60L * 60L * 1000L
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val thread = AgentSessionThread(
      provider = AgentSessionProvider.CODEX,
      id = "thread-1",
      title = "How much time need to compute A-C?",
      updatedAt = 14L * 24L * 60L * 60L * 1000L,
      archived = false,
    )
    val tooltip = buildSessionTreeThreadTooltipHtml(
      treeNode = SessionTreeNode.Thread(project, thread),
      now = now,
    )

    assertThat(tooltip).contains("<html>")
    assertThat(tooltip).contains("How much time need to compute A-C?")
    assertThat(tooltip).contains(AgentSessionsBundle.message("toolwindow.updated", "2w"))
    assertThat(tooltip).contains(
      AgentSessionsBundle.message(
        "toolwindow.thread.status",
        AgentSessionsBundle.message("toolwindow.thread.status.ready"),
      )
    )
  }

  @Test
  fun threadTooltipContainsFriendlyStatusForEachActivity() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val statusByActivity = mapOf(
      AgentThreadActivity.READY to "toolwindow.thread.status.ready",
      AgentThreadActivity.PROCESSING to "toolwindow.thread.status.in.progress",
      AgentThreadActivity.REVIEWING to "toolwindow.thread.status.needs.review",
      AgentThreadActivity.UNREAD to "toolwindow.thread.status.needs.input",
    )

    statusByActivity.forEach { (activity, statusKey) ->
      val thread = AgentSessionThread(
        provider = AgentSessionProvider.CODEX,
        id = "thread-$activity",
        title = "Status thread",
        updatedAt = 14L * 24L * 60L * 60L * 1000L,
        archived = false,
        activity = activity,
      )
      val tooltip = buildSessionTreeThreadTooltipHtml(
        treeNode = SessionTreeNode.Thread(project, thread),
        now = 28L * 24L * 60L * 60L * 1000L,
      )

      assertThat(tooltip).contains(
        AgentSessionsBundle.message(
          "toolwindow.thread.status",
          AgentSessionsBundle.message(statusKey),
        )
      )
    }
  }

  @Test
  fun threadTooltipUsesConfiguredMaxWidthWhenProvided() {
    val now = 28L * 24L * 60L * 60L * 1000L
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val thread = AgentSessionThread(
      provider = AgentSessionProvider.CODEX,
      id = "thread-1",
      title = "How much time need to compute A-C?",
      updatedAt = 14L * 24L * 60L * 60L * 1000L,
      archived = false,
    )
    val tooltip = buildSessionTreeThreadTooltipHtml(
      treeNode = SessionTreeNode.Thread(project, thread),
      now = now,
      maxWidthPx = 260,
    )

    assertThat(tooltip).contains("<body style='width:260px;'>")
  }

  @Test
  fun threadTooltipUsesUnknownUpdatedLabelWhenTimestampMissing() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val thread = AgentSessionThread(
      provider = AgentSessionProvider.CODEX,
      id = "thread-1",
      title = "How much time",
      updatedAt = 0L,
      archived = false,
    )
    val tooltip = buildSessionTreeThreadTooltipHtml(
      treeNode = SessionTreeNode.Thread(project, thread),
      now = System.currentTimeMillis(),
    )

    assertThat(tooltip).contains("How much time")
    assertThat(tooltip).contains(AgentSessionsBundle.message("toolwindow.updated", AgentSessionsBundle.message("toolwindow.time.unknown")))
  }

  @Test
  fun threadTrailingMetadataUsesSharedTimeColumnForStableReserveWidth() {
    val tree = createTree(width = 460)
    val metrics = tree.getFontMetrics(tree.font)
    val sharedTimeColumnWidth = metrics.stringWidth("11mo")

    val shortTrailing = computeSessionTreeThreadTrailingPaint(
      tree = tree,
      actionRightPadding = 0,
      timeLabel = "2w",
      fontMetrics = metrics,
      sharedTimeColumnWidth = sharedTimeColumnWidth,
    )
    val longTrailing = computeSessionTreeThreadTrailingPaint(
      tree = tree,
      actionRightPadding = 0,
      timeLabel = "11mo",
      fontMetrics = metrics,
      sharedTimeColumnWidth = sharedTimeColumnWidth,
    )

    assertThat(shortTrailing).isNotNull()
    assertThat(longTrailing).isNotNull()
    shortTrailing ?: return
    longTrailing ?: return

    assertThat(shortTrailing.timeRightBoundary).isEqualTo(longTrailing.timeRightBoundary)
    assertThat(shortTrailing.reserveWidth).isEqualTo(longTrailing.reserveWidth)
    assertThat(shortTrailing.timeX).isNotEqualTo(longTrailing.timeX)
    assertThat(shortTrailing.timeX + shortTrailing.timeTextWidth).isEqualTo(shortTrailing.timeRightBoundary)
    assertThat(longTrailing.timeX + longTrailing.timeTextWidth).isEqualTo(longTrailing.timeRightBoundary)
  }

  @Test
  fun threadTimePaintXClampsWhenPreferredPositionFallsOutsideRendererWidth() {
    val rendererWidth = 180
    val timeTextWidth = 20
    val maxVisibleX = resolveSessionTreeThreadTimePaintX(
      preferredX = Int.MAX_VALUE,
      rendererWidth = rendererWidth,
      timeTextWidth = timeTextWidth,
    )

    val clampedX = resolveSessionTreeThreadTimePaintX(
      preferredX = 420,
      rendererWidth = rendererWidth,
      timeTextWidth = timeTextWidth,
    )

    assertThat(clampedX).isEqualTo(maxVisibleX)
  }

  @Test
  fun threadTimePaintXPreservesPreferredPositionWhenItIsVisible() {
    val preferredX = 30

    val x = resolveSessionTreeThreadTimePaintX(
      preferredX = preferredX,
      rendererWidth = 180,
      timeTextWidth = 20,
    )

    assertThat(x).isEqualTo(preferredX)
  }

  @Test
  fun threadTimePaintXRespectsSelectionRightInset() {
    val withoutInset = resolveSessionTreeThreadTimePaintX(
      preferredX = Int.MAX_VALUE,
      rendererWidth = 180,
      timeTextWidth = 20,
      selectionRightInset = 0,
    )

    val withInset = resolveSessionTreeThreadTimePaintX(
      preferredX = Int.MAX_VALUE,
      rendererWidth = 180,
      timeTextWidth = 20,
      selectionRightInset = 16,
    )

    assertThat(withInset).isLessThan(withoutInset)
  }

  @Test
  fun threadRendererDoesNotQueryTreeLayoutApisDuringCellCustomization() {
    val now = 14L * 24L * 60L * 60L * 1000L
    val tree = ProbeTree().apply {
      setSize(420, 320)
      doLayout()
    }
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val thread = AgentSessionThread(
      provider = AgentSessionProvider.CODEX,
      id = "thread-1",
      title = "How much time",
      updatedAt = 0L,
      archived = false,
    )
    val threadId = SessionTreeId.Thread(project.path, thread.provider, thread.id)
    val renderer = SessionTreeCellRenderer(
      nowProvider = { now },
      rowActionsProvider = { _, _, _ -> null },
      nodeResolver = { id ->
        if (id == threadId) SessionTreeNode.Thread(project, thread) else null
      },
      providerIconProvider = { EmptyIcon.create(12, 12) },
    )

    renderer.getTreeCellRendererComponent(tree, descriptorValue(threadId), false, false, true, 0, false)

    assertThat(tree.visibleRectQueried).isFalse()
    assertThat(tree.closestRowQueried).isFalse()
    assertThat(tree.pathForRowQueried).isFalse()
  }

  @Test
  fun threadPresentationUsesFallbackTitleWhenThreadTitleIsBlank() {
    val thread = AgentSessionThread(
      provider = AgentSessionProvider.CODEX,
      id = "abcdef123456",
      title = "\n  \t  ",
      updatedAt = 0L,
      archived = false,
    )
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)

    val presentation = buildSessionTreeThreadRowPresentation(
      treeNode = SessionTreeNode.Thread(project, thread),
      now = 0L,
    )

    assertThat(presentation.title).isEqualTo("Thread abcdef12")
  }

  @Test
  fun threadPresentationUsesUnknownTimePlaceholderWhenTimestampMissing() {
    val thread = AgentSessionThread(
      provider = AgentSessionProvider.CODEX,
      id = "abcdef123456",
      title = "How much time",
      updatedAt = 0L,
      archived = false,
    )
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)

    val presentation = buildSessionTreeThreadRowPresentation(
      treeNode = SessionTreeNode.Thread(project, thread),
      now = 0L,
    )

    assertThat(presentation.timeLabel).isEqualTo(AgentSessionsBundle.message("toolwindow.time.unknown"))
  }

  @Test
  fun extractSessionTreeIdReadsSessionTreeIdFromDescriptorElement() {
    val treeId = SessionTreeId.Project("/work/project-a")

    assertThat(extractSessionTreeId(descriptorValue(treeId))).isEqualTo(treeId)
  }

  @Test
  fun extractSessionTreeIdRejectsValuesOutsideDescriptorContract() {
    val treeId = SessionTreeId.Project("/work/project-a")

    assertThat(extractSessionTreeId(treeId)).isNull()
    assertThat(extractSessionTreeId("not-a-tree-id")).isNull()
    assertThat(extractSessionTreeId(TestDescriptor("not-a-tree-id"))).isNull()
  }

  private fun createTree(width: Int): JTree {
    return JTree().apply {
      setSize(width, 320)
      doLayout()
    }
  }

  private fun descriptorValue(id: SessionTreeId): NodeDescriptor<Any?> = TestDescriptor(id)

  private class ProbeTree : JTree() {
    var visibleRectQueried: Boolean = false
    var pathForRowQueried: Boolean = false
    var closestRowQueried: Boolean = false

    override fun getVisibleRect(): Rectangle {
      visibleRectQueried = true
      return super.getVisibleRect()
    }

    override fun getPathForRow(row: Int): TreePath? {
      pathForRowQueried = true
      return super.getPathForRow(row)
    }

    override fun getClosestRowForLocation(x: Int, y: Int): Int {
      closestRowQueried = true
      return super.getClosestRowForLocation(x, y)
    }
  }

  private class TestDescriptor(
    private val elementValue: Any?,
  ) : NodeDescriptor<Any?>(null, null) {
    override fun update(): Boolean = false

    override fun getElement(): Any? = elementValue
  }
}
