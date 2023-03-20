// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.DiffPreview;
import com.intellij.openapi.vcs.changes.EditorTabDiffPreviewManager;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Consider using {@link AsyncChangesBrowserBase} to avoid potentially-expensive tree building operations on EDT.
 */
public abstract class ChangesBrowserBase extends JPanel implements DataProvider {
  public static final DataKey<ChangesBrowserBase> DATA_KEY =
    DataKey.create("com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase");

  @NotNull protected final Project myProject;

  protected final ChangesTree myViewer;

  private final DefaultActionGroup myToolBarGroup = new DefaultActionGroup();
  private final DefaultActionGroup myPopupMenuGroup = new DefaultActionGroup();
  private final ActionToolbar myToolbar;
  private final int myToolbarAnchor;
  private final JScrollPane myViewerScrollPane;
  private final AnAction myShowDiffAction;

  @Nullable private Runnable myInclusionChangedListener;
  @Nullable private DiffPreview myDiffPreview;


  protected ChangesBrowserBase(@NotNull Project project,
                               boolean showCheckboxes,
                               boolean highlightProblems) {
    myProject = project;
    myViewer = createTreeList(project, showCheckboxes, highlightProblems);

    myToolbar = ActionManager.getInstance().createActionToolbar("ChangesBrowser", myToolBarGroup, true);
    myToolbar.setTargetComponent(this);
    myToolbarAnchor = getToolbarAnchor();
    myToolbar.setOrientation(isVerticalToolbar() ? SwingConstants.VERTICAL : SwingConstants.HORIZONTAL);

    myViewer.installPopupHandler(myPopupMenuGroup);

    myViewerScrollPane = ScrollPaneFactory.createScrollPane(myViewer, true);
    setViewerBorder(createViewerBorder());

    myShowDiffAction = new MyShowDiffAction();
  }

  @NotNull
  protected ChangesTree createTreeList(@NotNull Project project, boolean showCheckboxes, boolean highlightProblems) {
    return new ChangesBrowserTreeList(this, project, showCheckboxes, highlightProblems);
  }

  protected void init() {
    setLayout(new BorderLayout());
    setFocusable(false);

    JPanel topPanel = new JPanel(new BorderLayout());

    Component toolbarComponent = isVerticalToolbar()
                                 ? createToolbarComponent()
                                 : new TreeActionsToolbarPanel(createToolbarComponent(), myViewer);

    JComponent headerPanel = createHeaderPanel();
    if (headerPanel != null) topPanel.add(headerPanel, BorderLayout.EAST);

    switch (myToolbarAnchor) {
      case SwingConstants.TOP -> topPanel.add(toolbarComponent, BorderLayout.CENTER);
      case SwingConstants.BOTTOM -> add(toolbarComponent, BorderLayout.SOUTH);
      case SwingConstants.LEFT -> add(toolbarComponent, BorderLayout.WEST);
      case SwingConstants.RIGHT -> add(toolbarComponent, BorderLayout.EAST);
    }

    add(topPanel, BorderLayout.NORTH);
    add(createCenterPanel(), BorderLayout.CENTER);

    myToolBarGroup.addAll(createToolbarActions());
    myPopupMenuGroup.addAll(createPopupMenuActions());

    AnAction groupByAction = ActionManager.getInstance().getAction(ChangesTree.GROUP_BY_ACTION_GROUP);
    if (!ActionUtil.recursiveContainsAction(myToolBarGroup, groupByAction)) {
      myToolBarGroup.addSeparator();
      myToolBarGroup.add(groupByAction);
    }

    if (isVerticalToolbar()) {
      List<AnAction> treeActions = TreeActionsToolbarPanel.createTreeActions(myViewer);
      boolean hasTreeActions = ContainerUtil.exists(treeActions,
                                                    action -> ActionUtil.recursiveContainsAction(myToolBarGroup, action));
      if (!hasTreeActions) {
        myToolBarGroup.addSeparator();
        myToolBarGroup.addAll(treeActions);
      }
    }

    myShowDiffAction.registerCustomShortcutSet(this, null);
    DiffUtil.recursiveRegisterShortcutSet(myToolBarGroup, this, null);
  }

  @NotNull
  protected Border createViewerBorder() {
    return IdeBorderFactory.createBorder(SideBorder.ALL);
  }

  public void setViewerBorder(@NotNull Border border) {
    myViewerScrollPane.setBorder(border);
  }

  public void hideViewerBorder() {
    int borders = switch (myToolbarAnchor) {
      case SwingConstants.TOP -> SideBorder.TOP;
      case SwingConstants.BOTTOM -> SideBorder.BOTTOM;
      case SwingConstants.LEFT -> SideBorder.LEFT;
      case SwingConstants.RIGHT -> SideBorder.RIGHT;
      default -> SideBorder.NONE;
    };

    setViewerBorder(IdeBorderFactory.createBorder(borders));
  }

  @MagicConstant(intValues = {SwingConstants.TOP, SwingConstants.BOTTOM, SwingConstants.LEFT, SwingConstants.RIGHT})
  protected int getToolbarAnchor() {
    return SwingConstants.TOP;
  }

  private boolean isVerticalToolbar() {
    return myToolbarAnchor == SwingConstants.LEFT || myToolbarAnchor == SwingConstants.RIGHT;
  }

  @NotNull
  protected JComponent createToolbarComponent() {
    return myToolbar.getComponent();
  }

  @NotNull
  protected abstract DefaultTreeModel buildTreeModel();


  @Nullable
  protected ChangeDiffRequestChain.Producer getDiffRequestProducer(@NotNull Object userObject) {
    if (userObject instanceof Change) {
      return ChangeDiffRequestProducer.create(myProject, (Change)userObject);
    }
    return null;
  }


  @Nullable
  protected JComponent createHeaderPanel() {
    return null;
  }

  @NotNull
  protected JComponent createCenterPanel() {
    return myViewerScrollPane;
  }

  @NotNull
  protected List<AnAction> createToolbarActions() {
    return Collections.singletonList(myShowDiffAction);
  }

  @NotNull
  protected List<AnAction> createPopupMenuActions() {
    List<AnAction> actions = new ArrayList<>();
    actions.add(myShowDiffAction);
    ContainerUtil.addIfNotNull(actions, ActionManager.getInstance().getAction("Diff.ShowStandaloneDiff"));

    return actions;
  }

  @NotNull
  protected List<AnAction> createDiffActions() {
    return Collections.emptyList();
  }

  protected void onDoubleClick() {
    if (canShowDiff()) showDiff();
  }

  protected void onIncludedChanged() {
    if (myInclusionChangedListener != null) myInclusionChangedListener.run();
  }


  public void selectEntries(@NotNull Collection<?> changes) {
    myViewer.setSelectedChanges(changes);
  }

  public void setInclusionChangedListener(@Nullable Runnable value) {
    myInclusionChangedListener = value;
  }

  public void addToolbarAction(@NotNull AnAction action) {
    myToolBarGroup.add(action);
    action.registerCustomShortcutSet(this, null);
  }

  public void addToolbarSeparator() {
    myToolBarGroup.addSeparator();
  }


  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return myViewer.getPreferredFocusedComponent();
  }

  @NotNull
  public ActionToolbar getToolbar() {
    return myToolbar;
  }

  @NotNull
  public JScrollPane getViewerScrollPane() {
    return myViewerScrollPane;
  }

  @NotNull
  public ChangesTree getViewer() {
    return myViewer;
  }

  @NotNull
  public ChangesGroupingPolicyFactory getGrouping() {
    return myViewer.getGrouping();
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    if (DATA_KEY.is(dataId)) {
      return this;
    }
    Object viewerData = myViewer.getData(dataId);
    return viewerData != null ? viewerData : VcsTreeModelData.getData(myProject, myViewer, dataId);
  }


  @NotNull
  public AnAction getDiffAction() {
    return myShowDiffAction;
  }

  public boolean canShowDiff() {
    ListSelection<Object> selection = VcsTreeModelData.getListSelectionOrAll(myViewer);
    return ContainerUtil.exists(selection.getList(), entry -> getDiffRequestProducer(entry) != null);
  }

  @Nullable
  protected DiffPreview getShowDiffActionPreview() {
    return myDiffPreview;
  }

  public void setShowDiffActionPreview(@Nullable DiffPreview diffPreview) {
    myDiffPreview = diffPreview;
  }

  public void showDiff() {
    EditorTabDiffPreviewManager previewManager = EditorTabDiffPreviewManager.getInstance(myProject);
    DiffPreview diffPreview = getShowDiffActionPreview();
    if (diffPreview != null && previewManager.isEditorDiffPreviewAvailable()) {
      diffPreview.performDiffAction();
    }
    else {
      showStandaloneDiff(myProject, this);
    }
  }

  public static void showStandaloneDiff(@NotNull Project project, @NotNull ChangesBrowserBase changesBrowser) {
    showStandaloneDiff(project, changesBrowser, VcsTreeModelData.getListSelectionOrAll(changesBrowser.myViewer),
                       changesBrowser::getDiffRequestProducer);
  }

  public static <T> void showStandaloneDiff(@NotNull Project project,
                                            @NotNull ChangesBrowserBase changesBrowser,
                                            @NotNull ListSelection<T> selection,
                                            @NotNull NullableFunction<? super T, ? extends ChangeDiffRequestChain.Producer> getDiffRequestProducer) {
    ListSelection<ChangeDiffRequestChain.Producer> producers = selection.map(getDiffRequestProducer);
    DiffRequestChain chain = new ChangeDiffRequestChain(producers);
    changesBrowser.updateDiffContext(chain);
    DiffManager.getInstance().showDiff(project, chain, new DiffDialogHints(null, changesBrowser));
  }

  public static void selectObjectWithTag(@NotNull ChangesTree tree,
                                         @NotNull Object userObject,
                                         @Nullable ChangesBrowserNode.Tag tag) {
    DefaultMutableTreeNode root = tree.getRoot();
    if (tag != null) {
      DefaultMutableTreeNode tagNode = TreeUtil.findNodeWithObject(root, tag);
      if (tagNode != null) {
        root = tagNode;
      }
    }
    DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(root, userObject);
    if (node == null) return;
    TreeUtil.selectPath(tree, TreeUtil.getPathFromRoot(node), false);
  }

  public static class ShowStandaloneDiff implements AnActionExtensionProvider {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public boolean isActive(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      ChangesBrowserBase changesBrowser = e.getData(DATA_KEY);
      return project != null && changesBrowser != null && changesBrowser.canShowDiff();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      ChangesBrowserBase changesBrowser = e.getRequiredData(DATA_KEY);
      Project project = e.getRequiredData(CommonDataKeys.PROJECT);

      showStandaloneDiff(project, changesBrowser);
    }
  }

  protected void updateDiffContext(@NotNull DiffRequestChain chain) {
    chain.putUserData(DiffUserDataKeys.CONTEXT_ACTIONS, createDiffActions());
  }

  private class MyShowDiffAction extends DumbAwareAction {
    MyShowDiffAction() {
      ActionUtil.copyFrom(this, IdeActions.ACTION_SHOW_DIFF_COMMON);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(canShowDiff() || e.getInputEvent() instanceof KeyEvent);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (canShowDiff()) showDiff();
    }
  }

  private static class ChangesBrowserTreeList extends ChangesTree {
    @NotNull private final ChangesBrowserBase myBrowser;

    ChangesBrowserTreeList(@NotNull ChangesBrowserBase browser,
                           @NotNull Project project,
                           boolean showCheckboxes,
                           boolean highlightProblems) {
      super(project, showCheckboxes, highlightProblems);
      myBrowser = browser;
      setDoubleClickAndEnterKeyHandler(myBrowser::onDoubleClick);
      setInclusionListener(myBrowser::onIncludedChanged);
    }

    @Override
    public final void rebuildTree() {
      DefaultTreeModel newModel = myBrowser.buildTreeModel();
      updateTreeModel(newModel);
    }
  }
}

