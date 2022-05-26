// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame;

import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vcs.changes.ui.browser.ChangesFilterer;
import com.intellij.openapi.vcs.changes.ui.browser.FilterableChangesBrowser;
import com.intellij.openapi.vcs.history.VcsDiffUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.SideBorder;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.switcher.QuickActionProvider;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.index.IndexedDetails;
import com.intellij.vcs.log.history.FileHistoryUtil;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.MergedChange;
import com.intellij.vcs.log.impl.MergedChangeDiffRequestProvider;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogActionIds;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import com.intellij.vcs.log.util.VcsLogUtil;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.util.*;
import java.util.function.Consumer;

import static com.intellij.diff.util.DiffUserDataKeysEx.*;
import static com.intellij.openapi.vcs.history.VcsDiffUtil.getRevisionTitle;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.vcs.log.impl.MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS;
import static com.intellij.vcs.log.impl.MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES;

/**
 * Change browser for commits in the Log. For merge commits, can display changes to commits parents in separate groups.
 */
public final class VcsLogChangesBrowser extends FilterableChangesBrowser {
  @NotNull public static final DataKey<Boolean> HAS_AFFECTED_FILES = DataKey.create("VcsLogChangesBrowser.HasAffectedFiles");
  @NotNull private final MainVcsLogUiProperties myUiProperties;
  @NotNull private final Function<? super CommitId, ? extends VcsShortCommitDetails> myDataGetter;

  @NotNull private final VcsLogUiProperties.PropertiesChangeListener myListener;

  @NotNull private final Set<VirtualFile> myRoots = new HashSet<>();
  private boolean myHasMergeCommits = false;
  @NotNull private final List<Change> myChanges = new ArrayList<>();
  @NotNull private final Map<CommitId, Set<Change>> myChangesToParents = new LinkedHashMap<>();
  @Nullable private Collection<FilePath> myAffectedPaths;
  @NotNull private Consumer<StatusText> myUpdateEmptyText = this::updateEmptyText;
  @NotNull private final Wrapper myToolbarWrapper;
  @NotNull private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);
  @Nullable private EditorDiffPreview myEditorDiffPreview;

  VcsLogChangesBrowser(@NotNull Project project,
                       @NotNull MainVcsLogUiProperties uiProperties,
                       @NotNull Function<? super CommitId, ? extends VcsShortCommitDetails> getter,
                       boolean isWithEditorDiffPreview,
                       @NotNull Disposable parent) {
    super(project, false, false);
    myUiProperties = uiProperties;
    myDataGetter = getter;

    myListener = new VcsLogUiProperties.PropertiesChangeListener() {
      @Override
      public <T> void onPropertyChanged(@NotNull VcsLogUiProperties.VcsLogUiProperty<T> property) {
        if (SHOW_CHANGES_FROM_PARENTS.equals(property) || SHOW_ONLY_AFFECTED_CHANGES.equals(property)) {
          myViewer.rebuildTree();
        }
      }
    };
    myUiProperties.addChangeListener(myListener);

    Disposer.register(parent, this);

    JComponent toolbarComponent = getToolbar().getComponent();
    myToolbarWrapper = new Wrapper(toolbarComponent);
    GuiUtils.installVisibilityReferent(myToolbarWrapper, toolbarComponent);

    init();

    setEditorDiffPreview(isWithEditorDiffPreview && VcsLogUiUtil.isDiffPreviewInEditor(myProject));
    if (isWithEditorDiffPreview) {
      EditorTabDiffPreviewManager.getInstance(myProject).subscribeToPreviewVisibilityChange(this, () -> {
        setEditorDiffPreview(VcsLogUiUtil.isDiffPreviewInEditor(myProject));
      });
    }

    hideViewerBorder();
    myViewer.setEmptyText(VcsLogBundle.message("vcs.log.changes.select.commits.to.view.changes.status"));
    myViewer.rebuildTree();
  }

  @NotNull
  @Override
  protected JComponent createToolbarComponent() {
    return myToolbarWrapper;
  }

  @NotNull
  @Override
  protected JComponent createCenterPanel() {
    JComponent centerPanel = super.createCenterPanel();
    JScrollPane scrollPane = UIUtil.findComponentOfType(centerPanel, JScrollPane.class);
    if (scrollPane != null) {
      ComponentUtil.putClientProperty(scrollPane, UIUtil.KEEP_BORDER_SIDES, SideBorder.TOP);
    }
    return centerPanel;
  }

  public void setToolbarHeightReferent(@NotNull JComponent referent) {
    myToolbarWrapper.setVerticalSizeReferent(referent);
  }

  public void addListener(@NotNull Listener listener, @NotNull Disposable disposable) {
    myDispatcher.addListener(listener, disposable);
  }

  @Override
  public void dispose() {
    super.dispose();
    myUiProperties.removeChangeListener(myListener);
  }

  @NotNull
  @Override
  protected List<AnAction> createToolbarActions() {
    return ContainerUtil.append(
      super.createToolbarActions(),
      CustomActionsSchema.getInstance().getCorrectedAction(VcsActions.VCS_LOG_CHANGES_BROWSER_TOOLBAR)
    );
  }

  @NotNull
  @Override
  protected List<AnAction> createPopupMenuActions() {
    return ContainerUtil.append(
      super.createPopupMenuActions(),
      ActionManager.getInstance().getAction(VcsLogActionIds.CHANGES_BROWSER_POPUP_ACTION_GROUP)
    );
  }

  private void updateModel(@NotNull Runnable update) {
    myChanges.clear();
    myChangesToParents.clear();
    myRoots.clear();
    myHasMergeCommits = false;
    myUpdateEmptyText = this::updateEmptyText;

    update.run();

    myUpdateEmptyText.accept(myViewer.getEmptyText());
    myViewer.rebuildTree();
    myDispatcher.getMulticaster().onModelUpdated();
  }

  public void resetSelectedDetails() {
    updateModel(() -> myUpdateEmptyText = text -> text.setText(""));
  }

  public void showText(@NotNull Consumer<StatusText> statusTextConsumer) {
    updateModel(() -> myUpdateEmptyText = statusTextConsumer);
  }

  @Override
  protected void onActiveChangesFilterChanges() {
    super.onActiveChangesFilterChanges();
    myUpdateEmptyText.accept(myViewer.getEmptyText());
  }

  public void setAffectedPaths(@Nullable Collection<FilePath> paths) {
    myAffectedPaths = paths;
    myUpdateEmptyText.accept(myViewer.getEmptyText());
    myViewer.rebuildTree();
  }

  public void setSelectedDetails(@NotNull List<? extends VcsFullCommitDetails> detailsList) {
    updateModel(() -> {
      if (!detailsList.isEmpty()) {
        myRoots.addAll(ContainerUtil.map(detailsList, detail -> detail.getRoot()));
        myHasMergeCommits = ContainerUtil.exists(detailsList, detail -> detail.getParents().size() > 1);

        if (detailsList.size() == 1) {
          VcsFullCommitDetails detail = Objects.requireNonNull(getFirstItem(detailsList));
          myChanges.addAll(detail.getChanges());

          if (detail.getParents().size() > 1) {
            for (int i = 0; i < detail.getParents().size(); i++) {
              Set<Change> changesSet = new ReferenceOpenHashSet<>(detail.getChanges(i));
              myChangesToParents.put(new CommitId(detail.getParents().get(i), detail.getRoot()), changesSet);
            }
          }
        }
        else {
          myChanges.addAll(VcsLogUtil.collectChanges(detailsList, VcsFullCommitDetails::getChanges));
        }
      }
    });
  }

  private void updateEmptyText(@NotNull StatusText emptyText) {
    if (myRoots.isEmpty()) {
      emptyText.setText(VcsLogBundle.message("vcs.log.changes.select.commits.to.view.changes.status"));
    }
    else if (!myChangesToParents.isEmpty()) {
      emptyText.setText(VcsLogBundle.message("vcs.log.changes.no.merge.conflicts.status")).
        appendSecondaryText(VcsLogBundle.message("vcs.log.changes.show.changes.to.parents.status.action"),
                            SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                            e -> myUiProperties.set(SHOW_CHANGES_FROM_PARENTS, true));
    }
    else if (isShowOnlyAffectedSelected() && myAffectedPaths != null) {
      emptyText.setText(VcsLogBundle.message("vcs.log.changes.no.changes.that.affect.selected.paths.status"))
        .appendSecondaryText(VcsLogBundle.message("vcs.log.changes.show.all.paths.status.action"),
                             SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                             e -> myUiProperties.set(SHOW_ONLY_AFFECTED_CHANGES, false));
    }
    else if (!myHasMergeCommits && hasActiveChangesFilter()) {
      emptyText.setText(VcsLogBundle.message("vcs.log.changes.no.changes.that.affect.selected.filters.status"))
        .appendSecondaryText(VcsLogBundle.message("vcs.log.changes.show.all.changes.status.action"),
                             SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                             e -> clearActiveChangesFilter());
    }
    else {
      emptyText.setText("");
    }
  }

  @NotNull
  @Override
  protected DefaultTreeModel buildTreeModel() {
    List<Change> changes = collectAffectedChanges(myChanges);
    ChangesFilterer.FilteredState filteredState = filterChanges(changes, !myHasMergeCommits);

    Map<CommitId, Collection<Change>> changesToParents = new LinkedHashMap<>();
    for (Map.Entry<CommitId, Set<Change>> entry : myChangesToParents.entrySet()) {
      changesToParents.put(entry.getKey(), collectAffectedChanges(entry.getValue()));
    }

    TreeModelBuilder builder = new TreeModelBuilder(myProject, getGrouping());
    setFilteredChanges(builder, filteredState, null);

    if (isShowChangesFromParents() && !changesToParents.isEmpty()) {
      if (changes.isEmpty()) {
        builder.createTagNode(VcsLogBundle.message("vcs.log.changes.no.merge.conflicts.node"));
      }
      for (CommitId commitId : changesToParents.keySet()) {
        Collection<Change> changesFromParent = changesToParents.get(commitId);
        if (!changesFromParent.isEmpty()) {
          ChangesBrowserNode<?> parentNode = new TagChangesBrowserNode(new ParentTag(commitId.getHash(), getText(commitId)),
                                                                       SimpleTextAttributes.REGULAR_ATTRIBUTES, false);
          parentNode.markAsHelperNode();

          builder.insertSubtreeRoot(parentNode);
          builder.insertChanges(changesFromParent, parentNode);
        }
      }
    }

    return builder.build();
  }

  @NotNull
  private List<Change> collectAffectedChanges(@NotNull Collection<Change> changes) {
    if (!isShowOnlyAffectedSelected() || myAffectedPaths == null) return new ArrayList<>(changes);
    return ContainerUtil.filter(changes, change -> ContainerUtil.or(myAffectedPaths, filePath -> {
      if (filePath.isDirectory()) {
        return FileHistoryUtil.affectsDirectory(change, filePath);
      }
      else {
        return FileHistoryUtil.affectsFile(change, filePath, false) ||
               FileHistoryUtil.affectsFile(change, filePath, true);
      }
    }));
  }

  private boolean isShowChangesFromParents() {
    return myUiProperties.exists(SHOW_CHANGES_FROM_PARENTS) &&
           myUiProperties.get(SHOW_CHANGES_FROM_PARENTS);
  }

  private boolean isShowOnlyAffectedSelected() {
    return myUiProperties.exists(SHOW_ONLY_AFFECTED_CHANGES) &&
           myUiProperties.get(SHOW_ONLY_AFFECTED_CHANGES);
  }

  @NotNull
  public List<Change> getDirectChanges() {
    return myChanges;
  }

  @NotNull
  public List<Change> getSelectedChanges() {
    return VcsTreeModelData.selected(myViewer).userObjects(Change.class);
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    if (VcsDataKeys.VCS.is(dataId)) {
      AbstractVcs vcs = getVcs();
      if (vcs == null) return null;
      return vcs.getKeyInstanceMethod();
    }
    else if (HAS_AFFECTED_FILES.is(dataId)) {
      return myAffectedPaths != null;
    }
    else if (QuickActionProvider.KEY.is(dataId)) {
      return new QuickActionProvider() {
        @Override
        public @NotNull List<AnAction> getActions(boolean originalProvider) {
          return SimpleToolWindowPanel.collectActions(VcsLogChangesBrowser.this);
        }

        @Override
        public JComponent getComponent() {
          return VcsLogChangesBrowser.this;
        }

        @Override
        public @NlsActions.ActionText @Nullable String getName() {
          return null;
        }
      };
    }
    return super.getData(dataId);
  }

  @Nullable
  private AbstractVcs getVcs() {
    List<AbstractVcs> allVcs = ContainerUtil.mapNotNull(myRoots, root -> ProjectLevelVcsManager.getInstance(myProject).getVcsFor(root));
    if (allVcs.size() == 1) return Objects.requireNonNull(getFirstItem(allVcs));

    Set<AbstractVcs> selectedVcs = ChangesUtil.getAffectedVcses(getSelectedChanges(), myProject);
    if (selectedVcs.size() == 1) return Objects.requireNonNull(getFirstItem(selectedVcs));

    return null;
  }

  @Override
  public ChangeDiffRequestChain.Producer getDiffRequestProducer(@NotNull Object userObject) {
    return getDiffRequestProducer(userObject, false);
  }

  @Nullable
  public ChangeDiffRequestChain.Producer getDiffRequestProducer(@NotNull Object userObject, boolean forDiffPreview) {
    if (!(userObject instanceof Change)) return null;
    Change change = (Change)userObject;

    Map<Key<?>, Object> context = new HashMap<>();
    if (!(change instanceof MergedChange)) {
      putRootTagIntoChangeContext(change, context);
    }
    return createDiffRequestProducer(myProject, change, context, forDiffPreview);
  }

  @Nullable
  public static ChangeDiffRequestChain.Producer createDiffRequestProducer(@NotNull Project project,
                                                                          @NotNull Change change,
                                                                          @NotNull Map<Key<?>, Object> context,
                                                                          boolean forDiffPreview) {
    if (change instanceof MergedChange) {
      MergedChange mergedChange = (MergedChange)change;
      if (mergedChange.getSourceChanges().size() == 2) {
        if (forDiffPreview) {
          putFilePathsIntoMergedChangeContext(mergedChange, context);
        }
        return new MergedChangeDiffRequestProvider.MyProducer(project, mergedChange, context);
      }
    }

    if (forDiffPreview) {
      VcsDiffUtil.putFilePathsIntoChangeContext(change, context);
    }

    return ChangeDiffRequestProducer.create(project, change, context);
  }

  public void setEditorDiffPreview(boolean isWithEditorDiffPreview) {
    EditorDiffPreview preview = myEditorDiffPreview;

    if (isWithEditorDiffPreview && preview == null) {
      preview = new VcsLogEditorDiffPreview(myProject, this);
      myEditorDiffPreview = preview;
    }
    else if (!isWithEditorDiffPreview && preview != null) {
      preview.closePreview();
      myEditorDiffPreview = null;
    }
  }

  @NotNull
  public VcsLogChangeProcessor createChangeProcessor(boolean isInEditor) {
    return new VcsLogChangeProcessor(myProject, this, isInEditor, this);
  }

  @Override
  protected @Nullable DiffPreview getShowDiffActionPreview() {
    return myEditorDiffPreview;
  }

  public void selectChange(@NotNull Object userObject, @Nullable ChangesBrowserNode.Tag tag) {
    selectObjectWithTag(myViewer, userObject, tag);
  }

  public @Nullable ChangesBrowserNode.Tag getTag(@NotNull Change change) {
    CommitId parentId = null;
    for (CommitId commitId : myChangesToParents.keySet()) {
      if (myChangesToParents.get(commitId).contains(change)) {
        parentId = commitId;
        break;
      }
    }

    if (parentId == null) return null;
    return new ParentTag(parentId.getHash(), getText(parentId));
  }

  private void putRootTagIntoChangeContext(@NotNull Change change, @NotNull Map<Key<?>, Object> context) {
    ChangesBrowserNode.Tag tag = getTag(change);
    if (tag != null) {
      context.put(ChangeDiffRequestProducer.TAG_KEY, tag);
    }
  }

  private static void putFilePathsIntoMergedChangeContext(@NotNull MergedChange change, @NotNull Map<Key<?>, Object> context) {
    ContentRevision centerRevision = change.getAfterRevision();
    ContentRevision leftRevision = change.getSourceChanges().get(0).getBeforeRevision();
    ContentRevision rightRevision = change.getSourceChanges().get(1).getBeforeRevision();
    FilePath centerFile = centerRevision == null ? null : centerRevision.getFile();
    FilePath leftFile = leftRevision == null ? null : leftRevision.getFile();
    FilePath rightFile = rightRevision == null ? null : rightRevision.getFile();
    context.put(VCS_DIFF_CENTER_CONTENT_TITLE, getRevisionTitle(centerRevision, centerFile, null));
    context.put(VCS_DIFF_RIGHT_CONTENT_TITLE, getRevisionTitle(rightRevision, rightFile, centerFile));
    context.put(VCS_DIFF_LEFT_CONTENT_TITLE, getRevisionTitle(leftRevision, leftFile, centerFile == null ? rightFile : centerFile));
  }

  @NotNull
  @Nls
  private String getText(@NotNull CommitId commitId) {
    String text = VcsLogBundle.message("vcs.log.changes.changes.to.parent.node", commitId.getHash().toShortString());
    VcsShortCommitDetails detail = myDataGetter.fun(commitId);
    if (!(detail instanceof LoadingDetails) || (detail instanceof IndexedDetails)) {
      text += " " + StringUtil.shortenTextWithEllipsis(detail.getSubject(), 50, 0);
    }
    return text;
  }

  public interface Listener extends EventListener {
    void onModelUpdated();
  }

  private static class ParentTag extends ChangesBrowserNode.ValueTag<Hash> {
    private final @NotNull @Nls String myText;

    ParentTag(@NotNull Hash commit, @NotNull @Nls String text) {
      super(commit);
      myText = text;
    }

    @Override
    public String toString() {
      return myText;
    }
  }
}
