// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame;

import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vcs.history.ShortVcsRevisionNumber;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.index.IndexedDetails;
import com.intellij.vcs.log.history.FileHistoryKt;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.MergedChange;
import com.intellij.vcs.log.impl.MergedChangeDiffRequestProvider;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogActionPlaces;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.DefaultTreeModel;
import java.util.*;

import static com.intellij.diff.util.DiffUserDataKeysEx.*;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.vcs.log.impl.MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS;

/**
 * Change browser for commits in the Log. For merge commits, can display changes to commits parents in separate groups.
 */
public class VcsLogChangesBrowser extends ChangesBrowserBase implements Disposable {
  @NotNull private static final String EMPTY_SELECTION_TEXT = "Select commit to view details";
  @NotNull private final Project myProject;
  @NotNull private final MainVcsLogUiProperties myUiProperties;
  @NotNull private final Function<? super CommitId, ? extends VcsShortCommitDetails> myDataGetter;

  @NotNull private final VcsLogUiProperties.PropertiesChangeListener myListener;

  @NotNull private final Set<VirtualFile> myRoots = ContainerUtil.newHashSet();
  @NotNull private final List<Change> myChanges = ContainerUtil.newArrayList();
  @NotNull private final Map<CommitId, Set<Change>> myChangesToParents = ContainerUtil.newHashMap();
  @NotNull private final Wrapper myToolbarWrapper;
  @Nullable private Runnable myModelUpdateListener;

  VcsLogChangesBrowser(@NotNull Project project,
                       @NotNull MainVcsLogUiProperties uiProperties,
                       @NotNull Function<? super CommitId, ? extends VcsShortCommitDetails> getter,
                       @NotNull Disposable parent) {
    super(project, false, false);
    myProject = project;
    myUiProperties = uiProperties;
    myDataGetter = getter;

    myListener = new VcsLogUiProperties.PropertiesChangeListener() {
      @Override
      public <T> void onPropertyChanged(@NotNull VcsLogUiProperties.VcsLogUiProperty<T> property) {
        if (SHOW_CHANGES_FROM_PARENTS.equals(property)) {
          myViewer.rebuildTree();
        }
      }
    };
    myUiProperties.addChangeListener(myListener);

    Disposer.register(parent, this);

    myToolbarWrapper = new Wrapper(getToolbar().getComponent());

    init();

    myViewer.setEmptyText(EMPTY_SELECTION_TEXT);
    myViewer.rebuildTree();
  }

  @NotNull
  @Override
  protected JComponent createToolbarComponent() {
    return myToolbarWrapper;
  }

  @NotNull
  @Override
  protected Border createViewerBorder() {
    return IdeBorderFactory.createBorder(SideBorder.TOP);
  }

  public void setToolbarHeightReferent(@NotNull JComponent referent) {
    myToolbarWrapper.setVerticalSizeReferent(referent);
  }

  public void setModelUpdateListener(@Nullable Runnable runnable) {
    myModelUpdateListener = runnable;
  }

  @Override
  public void dispose() {
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
      ActionManager.getInstance().getAction(VcsLogActionPlaces.CHANGES_BROWSER_POPUP_ACTION_GROUP)
    );
  }

  public void resetSelectedDetails() {
    myChanges.clear();
    myChangesToParents.clear();
    myRoots.clear();
    myViewer.setEmptyText("");
    myViewer.rebuildTree();
    if (myModelUpdateListener != null) myModelUpdateListener.run();
  }

  public void setSelectedDetails(@NotNull List<? extends VcsFullCommitDetails> detailsList) {
    myChanges.clear();
    myChangesToParents.clear();
    myRoots.clear();

    myRoots.addAll(ContainerUtil.map(detailsList, detail -> detail.getRoot()));

    if (detailsList.isEmpty()) {
      myViewer.setEmptyText(EMPTY_SELECTION_TEXT);
    }
    else if (detailsList.size() == 1) {
      VcsFullCommitDetails detail = notNull(getFirstItem(detailsList));
      myChanges.addAll(detail.getChanges());

      if (detail.getParents().size() > 1) {
        for (int i = 0; i < detail.getParents().size(); i++) {
          THashSet<Change> changesSet = ContainerUtil.newIdentityTroveSet(detail.getChanges(i));
          myChangesToParents.put(new CommitId(detail.getParents().get(i), detail.getRoot()), changesSet);
        }
      }

      if (myChanges.isEmpty() && detail.getParents().size() > 1) {
        myViewer.getEmptyText().setText("No merged conflicts.").
          appendSecondaryText("Show changes to parents", VcsLogUiUtil.getLinkAttributes(),
                              e -> myUiProperties.set(SHOW_CHANGES_FROM_PARENTS, true));
      }
      else {
        myViewer.setEmptyText("");
      }
    }
    else {
      myChanges.addAll(VcsLogUtil.collectChanges(detailsList, VcsFullCommitDetails::getChanges));
      myViewer.setEmptyText("");
    }

    myViewer.rebuildTree();
    if (myModelUpdateListener != null) myModelUpdateListener.run();
  }

  @NotNull
  @Override
  protected DefaultTreeModel buildTreeModel() {
    MyTreeModelBuilder builder = new MyTreeModelBuilder();
    builder.setChanges(myChanges, null);

    if (isShowChangesFromParents() && !myChangesToParents.isEmpty()) {
      if (myChanges.isEmpty()) {
        builder.addEmptyTextNode("No merged conflicts");
      }
      for (CommitId commitId : myChangesToParents.keySet()) {
        Collection<Change> changesFromParent = myChangesToParents.get(commitId);
        if (!changesFromParent.isEmpty()) {
          builder.addChangesFromParentNode(changesFromParent, commitId);
        }
      }
    }

    return builder.build();
  }

  private boolean isShowChangesFromParents() {
    return myUiProperties.exists(SHOW_CHANGES_FROM_PARENTS) &&
           myUiProperties.get(SHOW_CHANGES_FROM_PARENTS);
  }

  @NotNull
  public List<Change> getDirectChanges() {
    return myChanges;
  }

  @NotNull
  public List<Change> getSelectedChanges() {
    return VcsTreeModelData.selected(myViewer).userObjects(Change.class);
  }

  @NotNull
  public List<Change> getAllChanges() {
    return VcsTreeModelData.all(myViewer).userObjects(Change.class);
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    if (VcsDataKeys.VCS.is(dataId)) {
      AbstractVcs vcs = getVcs();
      if (vcs == null) return null;
      return vcs.getKeyInstanceMethod();
    }
    return super.getData(dataId);
  }

  @Nullable
  private AbstractVcs getVcs() {
    List<AbstractVcs> allVcs = ContainerUtil.mapNotNull(myRoots, root -> ProjectLevelVcsManager.getInstance(myProject).getVcsFor(root));
    if (allVcs.size() == 1) return notNull(getFirstItem(allVcs));

    Set<AbstractVcs> selectedVcs = ChangesUtil.getAffectedVcses(getSelectedChanges(), myProject);
    if (selectedVcs.size() == 1) return notNull(getFirstItem(selectedVcs));

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

    Map<Key, Object> context = ContainerUtil.newHashMap();
    if (!(change instanceof MergedChange)) {
      putRootTagIntoChangeContext(change, context);
    }
    return createDiffRequestProducer(myProject, change, context, forDiffPreview);
  }

  @Nullable
  public static ChangeDiffRequestChain.Producer createDiffRequestProducer(@NotNull Project project,
                                                                          @NotNull Change change,
                                                                          @NotNull Map<Key, Object> context,
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
      putFilePathsIntoChangeContext(change, context);
    }

    return ChangeDiffRequestProducer.create(project, change, context);
  }

  private void putRootTagIntoChangeContext(@NotNull Change change, @NotNull Map<Key, Object> context) {
    CommitId parentId = null;
    for (CommitId commitId : myChangesToParents.keySet()) {
      if (myChangesToParents.get(commitId).contains(change)) {
        parentId = commitId;
        break;
      }
    }

    if (parentId != null) {
      RootTag tag = new RootTag(parentId.getHash(), getText(parentId));
      context.put(ChangeDiffRequestProducer.TAG_KEY, tag);
    }
  }

  private static void putFilePathsIntoMergedChangeContext(@NotNull MergedChange change, @NotNull Map<Key, Object> context) {
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

  private static void putFilePathsIntoChangeContext(@NotNull Change change, @NotNull Map<Key, Object> context) {
    ContentRevision afterRevision = change.getAfterRevision();
    ContentRevision beforeRevision = change.getBeforeRevision();
    FilePath aFile = afterRevision == null ? null : afterRevision.getFile();
    FilePath bFile = beforeRevision == null ? null : beforeRevision.getFile();
    context.put(VCS_DIFF_RIGHT_CONTENT_TITLE, getRevisionTitle(afterRevision, aFile, null));
    context.put(VCS_DIFF_LEFT_CONTENT_TITLE, getRevisionTitle(beforeRevision, bFile, aFile));
  }

  @NotNull
  private static String getRevisionTitle(@Nullable ContentRevision revision,
                                         @Nullable FilePath file,
                                         @Nullable FilePath baseFile) {
    return getShortHash(revision) +
           (file == null || FileHistoryKt.FILE_PATH_HASHING_STRATEGY.equals(baseFile, file)
            ? ""
            : " (" + getRelativeFileName(baseFile, file) + ")");
  }

  @NotNull
  private static String getShortHash(@Nullable ContentRevision revision) {
    if (revision == null) return "";
    VcsRevisionNumber revisionNumber = revision.getRevisionNumber();
    if (revisionNumber instanceof ShortVcsRevisionNumber) return ((ShortVcsRevisionNumber)revisionNumber).toShortString();
    return revisionNumber.asString();
  }

  @NotNull
  private static String getRelativeFileName(@Nullable FilePath baseFile, @NotNull FilePath file) {
    if (baseFile == null || !baseFile.getName().equals(file.getName())) return file.getName();
    FilePath aParentPath = baseFile.getParentPath();
    if (aParentPath == null) return file.getName();
    return VcsFileUtil.relativePath(aParentPath.getIOFile(), file.getIOFile());
  }

  private class MyTreeModelBuilder extends TreeModelBuilder {
    MyTreeModelBuilder() {
      super(VcsLogChangesBrowser.this.myProject, VcsLogChangesBrowser.this.getGrouping());
    }

    public void addEmptyTextNode(@NotNull String text) {
      ChangesBrowserEmptyTextNode textNode = new ChangesBrowserEmptyTextNode(text);
      textNode.markAsHelperNode();

      myModel.insertNodeInto(textNode, myRoot, myRoot.getChildCount());
    }

    public void addChangesFromParentNode(@NotNull Collection<? extends Change> changes, @NotNull CommitId commitId) {
      ChangesBrowserNode parentNode = new ChangesBrowserParentNode(commitId);
      parentNode.markAsHelperNode();

      myModel.insertNodeInto(parentNode, myRoot, myRoot.getChildCount());
      for (Change change : changes) {
        insertChangeNode(change, parentNode, createChangeNode(change, null));
      }
    }
  }

  private static class ChangesBrowserEmptyTextNode extends ChangesBrowserNode<String> {
    protected ChangesBrowserEmptyTextNode(@NotNull String text) {
      super(text);
    }
  }

  private class ChangesBrowserParentNode extends ChangesBrowserNode<String> {
    protected ChangesBrowserParentNode(@NotNull CommitId commitId) {
      super(getText(commitId));
    }
  }

  @NotNull
  private String getText(@NotNull CommitId commitId) {
    String text = "Changes to " + commitId.getHash().toShortString();
    VcsShortCommitDetails detail = myDataGetter.fun(commitId);
    if (!(detail instanceof LoadingDetails) || (detail instanceof IndexedDetails)) {
      text += " " + StringUtil.shortenTextWithEllipsis(detail.getSubject(), 50, 0);
    }
    return text;
  }

  private static class RootTag {
    @NotNull private final Hash myCommit;
    @NotNull private final String myText;

    RootTag(@NotNull Hash commit, @NotNull String text) {
      myCommit = commit;
      myText = text;
    }

    @Override
    public String toString() {
      return myText;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      RootTag tag = (RootTag)o;
      return Objects.equals(myCommit, tag.myCommit);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myCommit);
    }
  }
}