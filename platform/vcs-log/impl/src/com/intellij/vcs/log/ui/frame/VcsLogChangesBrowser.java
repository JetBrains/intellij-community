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
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.UI;
import com.intellij.util.containers.ContainerUtil;
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
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import java.util.*;
import java.util.function.BiFunction;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.vcs.log.impl.MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS;

/**
 * Change browser for commits in the Log. For merge commits, can display changes to commits parents in separate groups.
 */
class VcsLogChangesBrowser extends ChangesBrowserBase implements Disposable {
  @NotNull private final Project myProject;
  @NotNull private final MainVcsLogUiProperties myUiProperties;
  @NotNull private final BiFunction<Hash, VirtualFile, VcsShortCommitDetails> myDataGetter;

  @NotNull private final VcsLogUiProperties.PropertiesChangeListener myListener;

  @Nullable private VirtualFile myRoot;
  @NotNull private final List<Change> myChanges = ContainerUtil.newArrayList();
  @NotNull private final Map<Hash, Set<Change>> myChangesToParents = ContainerUtil.newHashMap();

  public VcsLogChangesBrowser(@NotNull Project project,
                              @NotNull MainVcsLogUiProperties uiProperties,
                              @NotNull BiFunction<Hash, VirtualFile, VcsShortCommitDetails> getter,
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

    init();

    getViewerScrollPane().setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
    myViewer.rebuildTree();
  }

  @Override
  public void dispose() {
    myUiProperties.removeChangeListener(myListener);
  }

  @NotNull
  @Override
  protected List<AnAction> createToolbarActions() {
    List<AnAction> result = new ArrayList<>(super.createToolbarActions());
    result.add(ActionManager.getInstance().getAction("Vcs.RepositoryChangesBrowserToolbar"));
    result.add(ActionManager.getInstance().getAction(VcsLogActionPlaces.VCS_LOG_QUICK_CHANGE_VIEW_SETTINGS_ACTION));
    return result;
  }

  public void resetSelectedDetails() {
    myRoot = null;
    myChanges.clear();
    myChangesToParents.clear();
    myViewer.setEmptyText("");
    myViewer.rebuildTree();
  }

  public void setSelectedDetails(@NotNull List<VcsFullCommitDetails> detailsList) {
    myRoot = null;
    myChanges.clear();
    myChangesToParents.clear();

    if (detailsList.isEmpty()) {
      myViewer.setEmptyText("No commits selected");
    }
    else if (detailsList.size() == 1) {
      VcsFullCommitDetails details = notNull(getFirstItem(detailsList));
      myRoot = details.getRoot();
      myChanges.addAll(details.getChanges());

      if (details.getParents().size() > 1) {
        for (int i = 0; i < details.getParents().size(); i++) {
          THashSet<Change> changesSet = ContainerUtil.newIdentityTroveSet(details.getChanges(i));
          myChangesToParents.put(details.getParents().get(i), changesSet);
        }
      }

      if (myChanges.isEmpty() && details.getParents().size() > 1) {
        myViewer.getEmptyText().setText("No merged conflicts.").
          appendSecondaryText("Show changes to parents", getLinkAttributes(),
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
  private static SimpleTextAttributes getLinkAttributes() {
    return new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN,
                                    UI.getColor("link.foreground"));
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
      for (Hash hash : myChangesToParents.keySet()) {
        Collection<Change> changesFromParent = myChangesToParents.get(hash);
        if (!changesFromParent.isEmpty()) {
          builder.addChangesFromParentNode(changesFromParent, hash, myRoot);
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
      if (myRoot != null) {
        AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(myRoot);
        return vcs == null ? null : vcs.getKeyInstanceMethod();
      }
      else {
        List<Change> selectedChanges = VcsTreeModelData.selected(myViewer).userObjects(Change.class);
        Set<AbstractVcs> abstractVcs = ChangesUtil.getAffectedVcses(selectedChanges, myProject);
        if (abstractVcs.size() == 1) return notNull(getFirstItem(abstractVcs)).getKeyInstanceMethod();
        return null;
      }
    }
    return super.getData(dataId);
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

      Hash parentHash = null;
      for (Hash hash : myChangesToParents.keySet()) {
        if (myChangesToParents.get(hash).contains(change)) {
          parentHash = hash;
          break;
        }
      }

      if (parentHash != null && myRoot != null) {
        RootTag tag = new RootTag(parentHash, getText(parentHash, myRoot));
        Map<Key, Object> context = Collections.singletonMap(ChangeDiffRequestProducer.TAG_KEY, tag);
        return ChangeDiffRequestProducer.create(myProject, change, context);
      }
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

    public void addChangesFromParentNode(@NotNull Collection<Change> changes, @NotNull Hash hash, VirtualFile root) {
      ChangesBrowserNode parentNode = new ChangesBrowserParentNode(hash, root);
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
    protected ChangesBrowserParentNode(@NotNull Hash parentCommit, @NotNull VirtualFile root) {
      super(getText(parentCommit, root));
    }
  }

  @NotNull
  private String getText(@NotNull Hash commit, @NotNull VirtualFile root) {
    String text = "Changes to " + commit.toShortString();
    VcsShortCommitDetails detail = myDataGetter.apply(commit, root);
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