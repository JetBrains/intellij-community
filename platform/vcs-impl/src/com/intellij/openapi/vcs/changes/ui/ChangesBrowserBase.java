// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffContextEx;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.text.TwosideTextDiffProvider;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUtil;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vcs.VcsActions;
import com.intellij.openapi.vcs.VcsApplicationSettings;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public abstract class ChangesBrowserBase extends JPanel implements DataProvider, Disposable  {
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
  @NotNull private final VcsApplicationSettings.SettingsChangeListener mySettingsChangeListener;


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

    mySettingsChangeListener = new VcsApplicationSettings.SettingsChangeListener() {
      @Override
      public void onSettingsChanged() {
        myViewer.rebuildTree();
      }
    };
    VcsApplicationSettings.getInstance().addChangeListener(mySettingsChangeListener);

    myViewerScrollPane = ScrollPaneFactory.createScrollPane(myViewer, true);
    myViewerScrollPane.setBorder(createViewerBorder());

    myShowDiffAction = new MyShowDiffAction();
  }

  @Override
  public void dispose() {
    VcsApplicationSettings.getInstance().removeChangeListener(mySettingsChangeListener);
  }

  @NotNull
  protected ChangesBrowserTreeList createTreeList(@NotNull Project project, boolean showCheckboxes, boolean highlightProblems) {
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
      case SwingConstants.TOP:
        topPanel.add(toolbarComponent, BorderLayout.CENTER);
        break;
      case SwingConstants.BOTTOM:
        add(toolbarComponent, BorderLayout.SOUTH);
        break;
      case SwingConstants.LEFT:
        add(toolbarComponent, BorderLayout.WEST);
        break;
      case SwingConstants.RIGHT:
        add(toolbarComponent, BorderLayout.EAST);
        break;
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
  }

  @NotNull
  protected Border createViewerBorder() {
    return IdeBorderFactory.createBorder(SideBorder.ALL);
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
    return ContainerUtil.list(
      myShowDiffAction,
      CustomActionsSchema.getInstance().getCorrectedAction(VcsActions.VCS_LOG_CHANGES_BROWSER_BASE_TOOLBAR)
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

  public void showDiff() {
    ListSelection<Object> selection = VcsTreeModelData.getListSelectionOrAll(myViewer);
    ListSelection<ChangeDiffRequestChain.Producer> producers = selection.map(this::getDiffRequestProducer);
    DiffRequestChain chain = new ChangeDiffRequestChain(producers.getList(), producers.getSelectedIndex());
    updateDiffContext(chain);
    DiffManager.getInstance().showDiff(myProject, chain, new DiffDialogHints(null, this));
  }

  protected void updateDiffContext(@NotNull DiffRequestChain chain) {
    chain.putUserData(DiffUserDataKeys.CONTEXT_ACTIONS, createDiffActions());
  }

  private class MyShowDiffAction extends DumbAwareAction implements UpdateInBackground {
    MyShowDiffAction() {
      ActionUtil.copyFrom(this, IdeActions.ACTION_SHOW_DIFF_COMMON);
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

  @NotNull
  protected List<Change> filterMinorChanges(@NotNull List<Change> changes) {
    if (!VcsApplicationSettings.getInstance().HIDE_MINOR_CHANGES) {
      return changes;
    }

    return ContainerUtil.filter(changes, change -> !isMinorChange(change));
  }

  @Nullable
  protected boolean isMinorChange(@NotNull Change change) {
    if (!VcsApplicationSettings.getInstance().HIDE_MINOR_CHANGES) {
      return false;
    }
    final ProgressIndicator indicator = new EmptyProgressIndicator();

    final ChangeDiffRequestChain chain = new ChangeDiffRequestChain(
      ContainerUtil.list(ChangeDiffRequestProducer.create(myProject, change, ContainerUtil.newHashMap())), 0
    );
    final MinorChangeDiffContext myContext = new MinorChangeDiffContext(chain);
    final TextDiffSettingsHolder.TextDiffSettings textSettings = TextDiffViewerUtil.getTextSettings(myContext);
    final DiffContentFactory contentFactory = DiffContentFactory.getInstance();

    ContentRevision beforeRevision = change.getBeforeRevision();
    ContentRevision afterRevision = change.getAfterRevision();

    if (beforeRevision == null || afterRevision == null) {
      return beforeRevision == afterRevision;
    }

    try {
      String beforeRevisionContent = beforeRevision.getContent();
      String afterRevisionContent = afterRevision.getContent();
      MinorChangeRequest request = new MinorChangeRequest(Arrays.asList(
        contentFactory.create(beforeRevisionContent, beforeRevision.getFile().getFileType()),
        contentFactory.create(afterRevisionContent, afterRevision.getFile().getFileType())
      ));
      final Disposable disposable = new Disposable() {
        @Override
        public void dispose() {

        }
      };

      TwosideTextDiffProvider provider = DiffUtil.createTextDiffProvider(myProject, request, textSettings, () -> {
      }, disposable);
      List<LineFragment> compare = provider.compare(beforeRevisionContent, afterRevisionContent, indicator);
      return compare.isEmpty();
    }
    catch (VcsException e) {
      VcsBalloonProblemNotifier.showOverVersionControlView(myProject, "failed to resolve file content from vcs\n"
                                                                      + e.getMessage(), MessageType.ERROR);
    }

    return false;
  }

  private static class MinorChangeRequest extends ContentDiffRequest {
    private final List<DiffContent> myContents;

    MinorChangeRequest(final List<DiffContent> myContents) {
      this.myContents = myContents;
    }

    @NotNull
    @Override
    public List<DiffContent> getContents() {
      return myContents;
    }

    @NotNull
    @Override
    public List<String> getContentTitles() {
      return ContainerUtil.list(null, null, null);
    }

    @Nullable
    @Override
    public String getTitle() {
      return "none";
    }
  }

  private class MinorChangeDiffContext extends DiffContextEx {
    @NotNull private final UserDataHolder myContext;

    MinorChangeDiffContext(@NotNull UserDataHolder context) {
      myContext = context;
    }

    @Override
    public void reopenDiffRequest() {
    }

    @Override
    public void reloadDiffRequest() {
    }

    @Override
    public void showProgressBar(boolean enabled) {
    }

    @Override
    public void setWindowTitle(@NotNull String title) {
    }

    @Nullable
    @Override
    public Project getProject() {
      return myProject;
    }

    @Override
    public boolean isFocusedInWindow() {
      return false;
    }

    @Override
    public boolean isWindowFocused() {
      return false;
    }

    @Override
    public void requestFocusInWindow() {
    }

    @Nullable
    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
      return myContext.getUserData(key);
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
      myContext.putUserData(key, value);
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

