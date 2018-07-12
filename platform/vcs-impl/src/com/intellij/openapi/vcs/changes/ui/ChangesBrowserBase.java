// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.List;

public abstract class ChangesBrowserBase extends JPanel implements DataProvider {
  public static final DataKey<ChangesBrowserBase> DATA_KEY =
    DataKey.create("com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase");

  @NotNull protected final Project myProject;

  protected final ChangesTree myViewer;

  private final DefaultActionGroup myToolBarGroup = new DefaultActionGroup();
  private final DefaultActionGroup myPopupMenuGroup = new DefaultActionGroup();
  private final ActionToolbar myToolbar;
  private final JScrollPane myViewerScrollPane;
  private final AnAction myShowDiffAction;

  @Nullable private Runnable myInclusionChangedListener;


  protected ChangesBrowserBase(@NotNull Project project,
                               boolean showCheckboxes,
                               boolean highlightProblems) {
    myProject = project;
    myViewer = createTreeList(project, showCheckboxes, highlightProblems);

    myToolbar = ActionManager.getInstance().createActionToolbar("ChangesBrowser", myToolBarGroup, true);
    myToolbar.setTargetComponent(this);

    myViewer.installPopupHandler(myPopupMenuGroup);

    myViewerScrollPane = ScrollPaneFactory.createScrollPane(myViewer);

    myShowDiffAction = new MyShowDiffAction();
  }

  @NotNull
  protected ChangesBrowserTreeList createTreeList(@NotNull Project project, boolean showCheckboxes, boolean highlightProblems) {
    return new ChangesBrowserTreeList(this, project, showCheckboxes, highlightProblems);
  }

  protected void init() {
    setLayout(new BorderLayout());
    setFocusable(false);

    JPanel topPanel = new JPanel(new BorderLayout());

    TreeActionsToolbarPanel toolbarPanel = new TreeActionsToolbarPanel(createToolbarComponent(), myViewer);
    topPanel.add(toolbarPanel, BorderLayout.CENTER);

    JComponent headerPanel = createHeaderPanel();
    if (headerPanel != null) topPanel.add(headerPanel, BorderLayout.EAST);

    add(topPanel, BorderLayout.NORTH);
    add(myViewerScrollPane, BorderLayout.CENTER);


    myToolBarGroup.addAll(createToolbarActions());
    myPopupMenuGroup.addAll(createPopupMenuActions());

    AnAction groupByAction = ActionManager.getInstance().getAction(ChangesTree.GROUP_BY_ACTION_GROUP);
    if (!ActionUtil.recursiveContainsAction(myToolBarGroup, groupByAction)) {
      myToolBarGroup.addSeparator();
      myToolBarGroup.add(groupByAction);
    }

    myShowDiffAction.registerCustomShortcutSet(this, null);
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
  protected List<AnAction> createToolbarActions() {
    return ContainerUtil.list(
      myShowDiffAction
    );
  }

  @NotNull
  protected List<AnAction> createPopupMenuActions() {
    return ContainerUtil.list(
      myShowDiffAction
    );
  }

  @NotNull
  protected List<AnAction> createDiffActions() {
    return ContainerUtil.list(
    );
  }

  protected void onDoubleClick() {
    showDiff();
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
  public Object getData(String dataId) {
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
    ListSelection<Object> selection = VcsTreeModelData.getListSelection(myViewer);
    return ContainerUtil.exists(selection.getList(), entry -> getDiffRequestProducer(entry) != null);
  }

  public void showDiff() {
    ListSelection<Object> selection = VcsTreeModelData.getListSelection(myViewer);
    ListSelection<ChangeDiffRequestChain.Producer> producers = selection.map(this::getDiffRequestProducer);
    DiffRequestChain chain = new ChangeDiffRequestChain(producers.getList(), producers.getSelectedIndex());
    updateDiffContext(chain);
    DiffManager.getInstance().showDiff(myProject, chain, new DiffDialogHints(null, this));
  }

  protected void updateDiffContext(@NotNull DiffRequestChain chain) {
    chain.putUserData(DiffUserDataKeys.CONTEXT_ACTIONS, createDiffActions());
  }

  private class MyShowDiffAction extends DumbAwareAction {
    public MyShowDiffAction() {
      ActionUtil.copyFrom(this, IdeActions.ACTION_SHOW_DIFF_COMMON);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(canShowDiff() || e.getInputEvent() instanceof KeyEvent);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      if (canShowDiff()) showDiff();
    }
  }


  protected static class ChangesBrowserTreeList extends ChangesTree {
    @NotNull private final ChangesBrowserBase myViewer;

    public ChangesBrowserTreeList(@NotNull ChangesBrowserBase viewer,
                                  @NotNull Project project,
                                  boolean showCheckboxes,
                                  boolean highlightProblems) {
      super(project, showCheckboxes, highlightProblems);
      myViewer = viewer;
      setDoubleClickHandler(myViewer::onDoubleClick);
      setInclusionListener(myViewer::onIncludedChanged);
    }

    @Override
    public final void rebuildTree() {
      DefaultTreeModel newModel = myViewer.buildTreeModel();
      updateTreeModel(newModel);
    }
  }
}

