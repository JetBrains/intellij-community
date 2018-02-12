/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.ui.*;
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
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.MergedChange;
import com.intellij.vcs.log.impl.MergedChangeDiffRequestProvider;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogActionPlaces;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.util.*;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.vcs.log.impl.MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS;

/**
 * Change browser for commits in the Log. For merge commits, can display changes to commits parents in separate groups.
 */
class VcsLogChangesBrowser extends ChangesBrowserBase implements Disposable {
  @NotNull private final Project myProject;
  @NotNull private final MainVcsLogUiProperties myUiProperties;
  @NotNull private final Function<CommitId, VcsShortCommitDetails> myDataGetter;

  @NotNull private final VcsLogUiProperties.PropertiesChangeListener myListener;

  @NotNull private final Set<VirtualFile> myRoots = ContainerUtil.newHashSet();
  @NotNull private final List<Change> myChanges = ContainerUtil.newArrayList();
  @NotNull private final Map<CommitId, Set<Change>> myChangesToParents = ContainerUtil.newHashMap();
  @NotNull private final Wrapper myToolbarWrapper;

  public VcsLogChangesBrowser(@NotNull Project project,
                              @NotNull MainVcsLogUiProperties uiProperties,
                              @NotNull Function<CommitId, VcsShortCommitDetails> getter,
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

    getViewerScrollPane().setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
    myViewer.rebuildTree();
  }

  @NotNull
  @Override
  protected JComponent createToolbarComponent() {
    return myToolbarWrapper;
  }

  public void setToolbarHeightReferent(@NotNull JComponent referent) {
    myToolbarWrapper.setVerticalSizeReferent(referent);
  }

  @Override
  public void dispose() {
    myUiProperties.removeChangeListener(myListener);
  }

  @NotNull
  @Override
  protected List<AnAction> createToolbarActions() {
    List<AnAction> result = new ArrayList<>(super.createToolbarActions());
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(VcsLogActionPlaces.CHANGES_BROWSER_ACTION_GROUP);
    Collections.addAll(result, group.getChildren(null));
    return result;
  }

  public void resetSelectedDetails() {
    myChanges.clear();
    myChangesToParents.clear();
    myRoots.clear();
    myViewer.setEmptyText("");
    myViewer.rebuildTree();
  }

  public void setSelectedDetails(@NotNull List<VcsFullCommitDetails> detailsList) {
    myChanges.clear();
    myChangesToParents.clear();
    myRoots.clear();

    myRoots.addAll(ContainerUtil.map(detailsList, detail -> detail.getRoot()));

    if (detailsList.isEmpty()) {
      myViewer.setEmptyText("No commits selected");
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
      List<Change> changes = ContainerUtil.newArrayList();
      List<VcsFullCommitDetails> detailsListReversed = ContainerUtil.reverse(detailsList);
      for (VcsFullCommitDetails detail : detailsListReversed) {
        changes.addAll(detail.getChanges());
      }

      myChanges.addAll(CommittedChangesTreeBrowser.zipChanges(changes));
      myViewer.setEmptyText("");
    }

    myViewer.rebuildTree();
  }

  @NotNull
  @Override
  protected DefaultTreeModel buildTreeModel(boolean showFlatten) {
    MyTreeModelBuilder builder = new MyTreeModelBuilder(showFlatten);
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
  public List<Change> getAllChanges() {
    return myChanges;
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

    List<Change> selectedChanges = VcsTreeModelData.selected(myViewer).userObjects(Change.class);
    Set<AbstractVcs> selectedVcs = ChangesUtil.getAffectedVcses(selectedChanges, myProject);
    if (selectedVcs.size() == 1) return notNull(getFirstItem(selectedVcs));

    return null;
  }

  @Nullable
  @Override
  protected ChangeDiffRequestChain.Producer getDiffRequestProducer(@NotNull Object userObject) {
    if (userObject instanceof MergedChange) {
      MergedChange mergedChange = (MergedChange)userObject;
      if (mergedChange.getSourceChanges().size() == 2) {
        return new MergedChangeDiffRequestProvider.MyProducer(myProject, mergedChange);
      }
    }
    if (userObject instanceof Change) {
      Change change = (Change)userObject;

      CommitId parentId = null;
      for (CommitId commitId : myChangesToParents.keySet()) {
        if (myChangesToParents.get(commitId).contains(change)) {
          parentId = commitId;
          break;
        }
      }

      if (parentId != null) {
        RootTag tag = new RootTag(parentId.getHash(), getText(parentId));
        Map<Key, Object> context = Collections.singletonMap(ChangeDiffRequestProducer.TAG_KEY, tag);
        return ChangeDiffRequestProducer.create(myProject, change, context);
      }

      return ChangeDiffRequestProducer.create(myProject, change);
    }
    return null;
  }

  private class MyTreeModelBuilder extends TreeModelBuilder {
    public MyTreeModelBuilder(boolean showFlatten) {
      super(VcsLogChangesBrowser.this.myProject, showFlatten);
    }

    public void addEmptyTextNode(@NotNull String text) {
      ChangesBrowserEmptyTextNode textNode = new ChangesBrowserEmptyTextNode(text);
      textNode.markAsHelperNode();

      myModel.insertNodeInto(textNode, myRoot, myRoot.getChildCount());
    }

    public void addChangesFromParentNode(@NotNull Collection<Change> changes, @NotNull CommitId commitId) {
      ChangesBrowserNode parentNode = new ChangesBrowserParentNode(commitId);
      parentNode.markAsHelperNode();

      myModel.insertNodeInto(parentNode, myRoot, myRoot.getChildCount());
      for (Change change : changes) {
        insertChangeNode(change, parentNode, createChangeNode(change, null));
      }
    }
  }

  private static class ChangesBrowserEmptyTextNode extends ChangesBrowserNode {
    protected ChangesBrowserEmptyTextNode(@NotNull String text) {
      super(text);
    }
  }

  private class ChangesBrowserParentNode extends ChangesBrowserNode {
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

    public RootTag(@NotNull Hash commit, @NotNull String text) {
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