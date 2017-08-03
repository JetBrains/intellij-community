/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ui.PlusMinusModify;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.OpenTHashSet;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/** should work under _external_ lock
 * just logic here: do modifications to group of change lists
 */
public class ChangeListWorker {
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangeListWorker");

  private final Project myProject;
  private final Map<String, LocalChangeListImpl> myMap;
  private LocalChangeListImpl myDefault;

  private final ChangeListsIndexes myIdx;
  private final ChangesDelta myDelta;
  private final Set<String> myListsToDisappear;

  private final Map<String, OpenTHashSet<Change>> myChangesBeforeUpdateMap = new HashMap<>();

  public ChangeListWorker(@NotNull Project project, @NotNull PlusMinusModify<BaseRevision> deltaListener) {
    myProject = project;
    myMap = new LinkedHashMap<>();
    myIdx = new ChangeListsIndexes();

    myDelta = new ChangesDelta(deltaListener);
    myListsToDisappear = ContainerUtil.newLinkedHashSet();

    ensureDefaultListExists();
  }

  private ChangeListWorker(ChangeListWorker worker) {
    myProject = worker.myProject;
    myMap = new LinkedHashMap<>();
    myIdx = new ChangeListsIndexes(worker.myIdx);
    myDelta = worker.myDelta;
    myListsToDisappear = ContainerUtil.newLinkedHashSet(worker.myListsToDisappear);

    for (LocalChangeListImpl list : worker.myMap.values()) {
      LocalChangeListImpl copy = list.copy();
      myMap.put(copy.getName(), copy);
      if (copy.isDefault()) {
        if (myDefault != null) {
          LOG.error("multiple default lists found when copy");
          copy.setDefault(false);
        }
        else {
          myDefault = copy;
        }
      }
    }

    ensureDefaultListExists();
  }

  private void ensureDefaultListExists() {
    if (myDefault != null) return;

    if (!myMap.isEmpty()) {
      myDefault = myMap.values().iterator().next();
      myDefault.setDefault(true);
    }
    else {
      myDefault = LocalChangeListImpl.createEmptyChangeListImpl(myProject, LocalChangeList.DEFAULT_NAME, null);
      myDefault.setDefault(true);
      myMap.put(myDefault.getName(), myDefault);
    }
  }

  public void onAfterWorkerSwitch(@NotNull final ChangeListWorker previous) {
    boolean somethingChanged = myDelta.notifyPathsChanged(previous.myIdx, myIdx);
    somethingChanged |= checkForMultipleCopiesNotMove();

    if (somethingChanged) {
      FileStatusManager.getInstance(myProject).fileStatusesChanged();
    }
  }

  private boolean checkForMultipleCopiesNotMove() {
    boolean somethingChanged = false;
    final MultiMap<FilePath, Pair<Change, String>> moves = new MultiMap<>();

    for (LocalChangeList changeList : myMap.values()) {
      final Collection<Change> changes = changeList.getChanges();
      for (Change change : changes) {
        if (change.isMoved() || change.isRenamed()) {
          moves.putValue(change.getBeforeRevision().getFile(), Pair.create(change, changeList.getName()));
        }
      }
    }
    for (FilePath filePath : moves.keySet()) {
      final List<Pair<Change, String>> copies = (List<Pair<Change, String>>)moves.get(filePath);
      if (copies.size() == 1) continue;
      copies.sort(CHANGES_AFTER_REVISION_COMPARATOR);
      for (int i = 0; i < (copies.size() - 1); i++) {
        somethingChanged = true;
        final Pair<Change, String> item = copies.get(i);
        final Change oldChange = item.getFirst();
        final Change newChange = new Change(null, oldChange.getAfterRevision());

        final LocalChangeListImpl list = myMap.get(item.getSecond());
        list.removeChange(oldChange);
        list.addChange(newChange);

        final VcsKey key = myIdx.getVcsFor(oldChange);
        myIdx.changeRemoved(oldChange);
        myIdx.changeAdded(newChange, key);
      }
    }
    return somethingChanged;
  }

  public ChangeListWorker copy() {
    return new ChangeListWorker(this);
  }

  @Nullable
  public LocalChangeList getChangeListByName(@Nullable String name) {
    return myMap.get(name);
  }

  @Nullable
  public LocalChangeList getChangeListCopyByName(@Nullable String name) {
    LocalChangeList list = getChangeListByName(name);
    return list != null ? list.copy() : null;
  }

  @Nullable
  public LocalChangeList getChangeListById(@Nullable String id) {
    for (LocalChangeList changeList : myMap.values()) {
      if (changeList.getId().equals(id)) {
        return changeList;
      }
    }
    return null;
  }

  /**
   * @return previous default list name or null if nothing was done
   */
  @Nullable
  public String setDefault(String name) {
    LocalChangeListImpl newDefault = myMap.get(name);
    if (newDefault == null) {
      return null;
    }

    String previousName = myDefault.getName();

    myDefault.setDefault(false);
    newDefault.setDefault(true);
    myDefault = newDefault;

    return previousName;
  }

  public boolean setReadOnly(String name, boolean value) {
    final LocalChangeListImpl list = myMap.get(name);
    if (list != null) {
      list.setReadOnlyImpl(value);
    }
    return list != null;
  }

  @NotNull
  public LocalChangeList addChangeList(@NotNull String name, @Nullable String comment, @Nullable Object data) {
    return addChangeList(name, comment, false, data);
  }

  @NotNull
  private LocalChangeList addChangeList(@NotNull String name, @Nullable String description, boolean inUpdate,
                                        @Nullable Object data) {
    if (myMap.containsKey(name)) {
      LOG.error("Attempt to create duplicate changelist " + name);
      return myMap.get(name);
    }

    LocalChangeListImpl newList = LocalChangeListImpl.createEmptyChangeListImpl(myProject, name, null);
    newList.setCommentImpl(description);
    newList.setData(data);

    myMap.put(name, newList);
    if (inUpdate) {
      startProcessingChanges(newList); // this is executed only when use through GATE
    }
    return newList;
  }

  void setChangeLists(@NotNull Collection<LocalChangeListImpl> lists) {
    myDefault = null;
    myMap.clear();
    myIdx.clear();

    for (LocalChangeListImpl list : lists) {
      myMap.put(list.getName(), list);
      if (list.isDefault()) {
        if (myDefault != null) {
          LOG.error("multiple default lists found");
          list.setDefault(false);
        }
        else {
          myDefault = list;
        }
      }

      for (Change change : list.getChanges()) {
        myIdx.changeAdded(change, null);
      }
    }

    ensureDefaultListExists();
  }

  private void addChangeToList(@NotNull LocalChangeListImpl list, @NotNull Change change, VcsKey vcsKey) {
    list.addChange(change);
    myIdx.changeAdded(change, vcsKey);
  }

  public void addChangeToList(@NotNull String name, @NotNull Change change, VcsKey vcsKey) {
    LOG.debug("[addChangeToList] name: " + name + " change: " + ChangesUtil.getFilePath(change).getPath() + " vcs: " +
              (vcsKey == null ? null : vcsKey.getName()));
    final LocalChangeListImpl changeList = myMap.get(name);
    if (changeList == null) return;

    addChangeToList(changeList, change, vcsKey);
  }

  public void addChangeToCorrespondingList(@NotNull Change change, VcsKey vcsKey) {
    final String path = ChangesUtil.getFilePath(change).getPath();
    LOG.debug("[addChangeToCorrespondingList] for change " + path  + " type: " + change.getType() + " have before revision: " + (change.getBeforeRevision() != null));
    for (LocalChangeListImpl list : myMap.values()) {
      OpenTHashSet<Change> changesBeforeUpdate = myChangesBeforeUpdateMap.get(list.getName());
      if (changesBeforeUpdate.contains(change)) {
        LOG.debug("[addChangeToCorrespondingList] matched: " + list.getName());
        addChangeToList(list, change, vcsKey);
        return;
      }
    }
    LOG.debug("[addChangeToCorrespondingList] added to default list");
    addChangeToList(myDefault, change, vcsKey);
  }

  public boolean removeChangeList(@NotNull String name) {
    final LocalChangeList list = myMap.get(name);
    if (list == null) return false;

    if (list.isDefault()) {
      LOG.error("Cannot remove default changelist");
      return false;
    }

    for (Change change : list.getChanges()) {
      myDefault.addChange(change);
    }

    myMap.remove(name);
    return true;
  }

  /**
   * @return moved changes and their old changelist
   */
  @Nullable
  public MultiMap<LocalChangeList, Change> moveChangesTo(String name, @NotNull Change[] changes) {
    final LocalChangeListImpl changeList = myMap.get(name);
    if (changeList != null) {
      final MultiMap<LocalChangeList, Change> result = new MultiMap<>();
      for (LocalChangeListImpl list : myMap.values()) {
        if (list.equals(changeList)) continue;
        for (Change change : changes) {
          final Change removedChange = list.removeChange(change);
          if (removedChange != null) {
            changeList.addChange(removedChange);
            result.putValue(list, removedChange);
          }
        }
      }
      return result;
    }
    return null;
  }

  public boolean editName(@NotNull String fromName, @NotNull String toName) {
    if (fromName.equals(toName)) return false;
    if (myMap.containsKey(toName)) return false;

    final LocalChangeListImpl list = myMap.get(fromName);
    if (list == null || list.isReadOnly()) return false;

    LocalChangeListImpl newList = list.copy(toName);
    myMap.remove(fromName);
    myMap.put(toName, newList);

    OpenTHashSet<Change> changesBeforeUpdateFrom = myChangesBeforeUpdateMap.remove(fromName);
    OpenTHashSet<Change> changesBeforeUpdateTo = myChangesBeforeUpdateMap.put(toName, changesBeforeUpdateFrom);
    LOG.assertTrue(changesBeforeUpdateTo == null, "old changes for new changelist name found during rename");

    return true;
  }

  @Nullable
  public String editComment(@NotNull String name, @Nullable String newComment) {
    final LocalChangeListImpl list = myMap.get(name);
    if (list == null) return null;

    final String oldComment = list.getComment();
    if (!Comparing.equal(oldComment, newComment)) {
      list.setCommentImpl(newComment);
    }
    return oldComment;
  }

  public boolean isEmpty() {
    return myMap.isEmpty();
  }

  @NotNull
  public LocalChangeList getDefaultList() {
    return myDefault;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  /**
   * called NOT under ChangeListManagerImpl lock
   */
  public void notifyStartProcessingChanges(@Nullable VcsModifiableDirtyScope scope) {
    List<Change> removedChanges = new ArrayList<>();
    for (LocalChangeListImpl list : myMap.values()) {
      startProcessingChanges(list);
      removedChanges.addAll(removeChangesUnderScope(list, scope));
    }

    // scope should be modified for correct moves tracking
    if (scope != null) {
      for (Change change : removedChanges) {
        if (change.isMoved() || change.isRenamed()) {
          scope.addDirtyFile(change.getBeforeRevision().getFile());
          scope.addDirtyFile(change.getAfterRevision().getFile());
        }
      }
    }
  }

  /**
   * called NOT under ChangeListManagerImpl lock
   */
  public void notifyDoneProcessingChanges(final ChangeListListener dispatcher) {
    List<ChangeList> changedLists = new ArrayList<>();
    final Map<LocalChangeListImpl, List<Change>> removedChanges = new HashMap<>();
    final Map<LocalChangeListImpl, List<Change>> addedChanges = new HashMap<>();
    for (LocalChangeListImpl list : myMap.values()) {
      final List<Change> removed = new ArrayList<>();
      final List<Change> added = new ArrayList<>();

      if (doneProcessingChanges(list, removed, added)) {
        changedLists.add(list);
      }
      if (!removed.isEmpty()) {
        removedChanges.put(list, removed);
      }
      if (!added.isEmpty()) {
        addedChanges.put(list, added);
      }
    }
    for (Map.Entry<LocalChangeListImpl, List<Change>> entry : removedChanges.entrySet()) {
      dispatcher.changesRemoved(entry.getValue(), entry.getKey());
    }
    for (Map.Entry<LocalChangeListImpl, List<Change>> entry : addedChanges.entrySet()) {
      dispatcher.changesAdded(entry.getValue(), entry.getKey());
    }
    for (ChangeList changeList : changedLists) {
      dispatcher.changeListChanged(changeList);
    }

    for (String name : myListsToDisappear) {
      final LocalChangeList changeList = myMap.get(name);
      if ((changeList != null) && changeList.getChanges().isEmpty() && (!changeList.isReadOnly()) && (!changeList.isDefault())) {
        removeChangeList(name);
      }
    }
    myListsToDisappear.clear();

    myChangesBeforeUpdateMap.clear();
  }

  private void startProcessingChanges(@NotNull LocalChangeListImpl list) {
    OpenTHashSet<Change> changesBeforeUpdate = new OpenTHashSet<>(list.getChanges());
    myChangesBeforeUpdateMap.put(list.getName(), changesBeforeUpdate);
  }

  @NotNull
  private List<Change> removeChangesUnderScope(@NotNull LocalChangeListImpl list, @Nullable VcsModifiableDirtyScope scope) {
    List<Change> removed = new ArrayList<>();
    for (Change change : list.getChanges()) {
      ContentRevision before = change.getBeforeRevision();
      ContentRevision after = change.getAfterRevision();
      boolean isUnderScope = scope == null ||
                             before != null && scope.belongsTo(before.getFile()) ||
                             after != null && scope.belongsTo(after.getFile()) ||
                             isIgnoredChange(before, after, myProject);
      if (isUnderScope) {
        list.removeChange(change);
        myIdx.changeRemoved(change);

        removed.add(change);
      }
    }
    return removed;
  }

  private static boolean isIgnoredChange(@Nullable ContentRevision before, @Nullable ContentRevision after, @NotNull Project project) {
    return isIgnoredRevision(before, project) && isIgnoredRevision(after, project);
  }

  private static boolean isIgnoredRevision(@Nullable ContentRevision revision, final @NotNull Project project) {
    if (revision == null) return true;
    return ReadAction.compute(() -> {
      if (project.isDisposed()) return false;
      VirtualFile vFile = revision.getFile().getVirtualFile();
      return vFile != null && ProjectLevelVcsManager.getInstance(project).isIgnored(vFile);
    });
  }

  private boolean doneProcessingChanges(@NotNull LocalChangeListImpl list,
                                        @NotNull List<Change> removedChanges,
                                        @NotNull List<Change> addedChanges) {
    OpenTHashSet<Change> changesBeforeUpdate = myChangesBeforeUpdateMap.get(list.getName());
    Set<Change> changes = list.getChanges();

    for (Change newChange : changes) {
      Change oldChange = findOldChange(changesBeforeUpdate, newChange);
      if (oldChange == null) {
        addedChanges.add(newChange);
      }
    }

    removedChanges.addAll(changesBeforeUpdate);
    removedChanges.removeAll(changes);

    return changes.size() != changesBeforeUpdate.size() ||
           !addedChanges.isEmpty() ||
           !removedChanges.isEmpty();
  }

  @Nullable
  private static Change findOldChange(@NotNull OpenTHashSet<Change> changesBeforeUpdate, @NotNull Change newChange) {
    Change oldChange = changesBeforeUpdate.get(newChange);
    if (oldChange != null && sameBeforeRevision(oldChange, newChange) &&
        newChange.getFileStatus().equals(oldChange.getFileStatus())) {
      return oldChange;
    }
    return null;
  }

  private static boolean sameBeforeRevision(@NotNull Change change1, @NotNull Change change2) {
    final ContentRevision b1 = change1.getBeforeRevision();
    final ContentRevision b2 = change2.getBeforeRevision();
    if (b1 != null && b2 != null) {
      final VcsRevisionNumber rn1 = b1.getRevisionNumber();
      final VcsRevisionNumber rn2 = b2.getRevisionNumber();
      final boolean isBinary1 = (b1 instanceof BinaryContentRevision);
      final boolean isBinary2 = (b2 instanceof BinaryContentRevision);
      return rn1 != VcsRevisionNumber.NULL && rn2 != VcsRevisionNumber.NULL && rn1.compareTo(rn2) == 0 && isBinary1 == isBinary2;
    }
    return b1 == null && b2 == null;
  }

  @NotNull
  public List<LocalChangeList> getChangeLists() {
    return new ArrayList<>(myMap.values());
  }

  @NotNull
  public List<File> getAffectedPaths() {
    final SortedSet<FilePath> set = myIdx.getAffectedPaths();
    final List<File> result = new ArrayList<>(set.size());
    for (FilePath path : set) {
      result.add(path.getIOFile());
    }
    return result;
  }

  @NotNull
  public List<VirtualFile> getAffectedFiles() {
    final Set<VirtualFile> result = ContainerUtil.newLinkedHashSet();
    for (LocalChangeList list : myMap.values()) {
      for (Change change : list.getChanges()) {
        final ContentRevision before = change.getBeforeRevision();
        final ContentRevision after = change.getAfterRevision();
        if (before != null) {
          final VirtualFile file = before.getFile().getVirtualFile();
          if (file != null) {
            result.add(file);
          }
        }
        if (after != null) {
          final VirtualFile file = after.getFile().getVirtualFile();
          if (file != null) {
            result.add(file);
          }
        }
      }
    }
    return new ArrayList<>(result);
  }

  @Nullable
  public LocalChangeList getChangeListFor(@NotNull VirtualFile file) {
    FilePath filePath = VcsUtil.getFilePath(file);

    Pair<LocalChangeListImpl, Change> pair = getChangeAndListByPath(filePath);
    if (pair == null) return null;

    return pair.first;
  }

  public void removeRegisteredChangeFor(@Nullable FilePath filePath) {
    myIdx.remove(filePath);

    Pair<LocalChangeListImpl, Change> pair = getChangeAndListByPath(filePath);
    if (pair == null) return;

    LocalChangeListImpl list = pair.first;
    Change change = pair.second;
    list.removeChange(change);
  }

  @Nullable
  public Change getChangeForPath(@Nullable FilePath filePath) {
    Pair<LocalChangeListImpl, Change> pair = getChangeAndListByPath(filePath);
    if (pair == null) return null;

    return pair.second;
  }

  @Nullable
  private Pair<LocalChangeListImpl, Change> getChangeAndListByPath(@Nullable FilePath filePath) {
    if (filePath == null) return null;
    for (LocalChangeListImpl list : myMap.values()) {
      for (Change change : list.getChanges()) {
        ContentRevision before = change.getBeforeRevision();
        ContentRevision after = change.getAfterRevision();
        if (before != null && before.getFile().equals(filePath) ||
            after != null && after.getFile().equals(filePath)) {
          return Pair.create(list, change);
        }
      }
    }
    return null;
  }

  @Nullable
  public FileStatus getStatus(@NotNull VirtualFile file) {
    return myIdx.getStatus(file);
  }

  @Nullable
  public FileStatus getStatus(@NotNull FilePath file) {
    return myIdx.getStatus(file);
  }

  @NotNull
  public Collection<Change> getAllChanges() {
    final Collection<Change> changes = new HashSet<>();
    for (LocalChangeList list : myMap.values()) {
      changes.addAll(list.getChanges());
    }
    return changes;
  }

  public int getChangeListsNumber() {
    return myMap.size();
  }

  @NotNull
  public Collection<LocalChangeList> getInvolvedListsFilterChanges(@NotNull Collection<Change> changes, @NotNull List<Change> validChanges) {
    Set<LocalChangeList> includedListsCopies = new HashSet<>();
    Map<Change, LocalChangeList> internalMap = new HashMap<>();

    for (LocalChangeList list : myMap.values()) {
      for (Change change : list.getChanges()) {
        internalMap.put(change, list);
      }
    }

    for (Change change : changes) {
      LocalChangeList list = internalMap.get(change);
      if (list != null) {
        includedListsCopies.add(list);
        validChanges.add(change);
      }
    }

    return includedListsCopies;
  }

  @Nullable
  public LocalChangeList getChangeListForChange(final Change change) {
    for (LocalChangeList list : myMap.values()) {
      if (list.getChanges().contains(change)) return list;
    }
    return null;
  }

  @Nullable
  public LocalChangeList getChangeListIfOnlyOne(@Nullable Change[] changes) {
    if (changes == null || changes.length == 0) {
      return null;
    }

    final Change first = changes[0];

    for (LocalChangeList list : myMap.values()) {
      final Collection<Change> listChanges = list.getChanges();
      if (listChanges.contains(first)) {
        // must contain all other
        for (int i = 1; i < changes.length; i++) {
          final Change change = changes[i];
          if (!listChanges.contains(change)) {
            return null;
          }
        }
        return list;
      }
    }
    return null;
  }

  public ThreeState haveChangesUnder(@NotNull VirtualFile virtualFile) {
    FilePath dir = VcsUtil.getFilePath(virtualFile);
    FilePath changeCandidate = myIdx.getAffectedPaths().ceiling(dir);
    if (changeCandidate == null) {
      return ThreeState.NO;
    }
    return FileUtil.isAncestorThreeState(dir.getPath(), changeCandidate.getPath(), false);
  }

  @NotNull
  public List<Change> getChangesIn(@NotNull FilePath dirPath) {
    List<Change> changes = new ArrayList<>();
    for (ChangeList list : myMap.values()) {
      for (Change change : list.getChanges()) {
        ContentRevision after = change.getAfterRevision();
        ContentRevision before = change.getBeforeRevision();
        if (after != null && after.getFile().isUnder(dirPath, false) ||
            before != null && before.getFile().isUnder(dirPath, false)) {
          changes.add(change);
        }
      }
    }
    return changes;
  }

  @Nullable
  public VcsKey getVcsFor(@NotNull Change change) {
    return myIdx.getVcsFor(change);
  }

  public void setListsToDisappear(@NotNull Collection<String> names) {
    myListsToDisappear.addAll(names);
  }

  @NotNull
  public ChangeListManagerGate createGate() {
    return new MyGate(this);
  }

  private static class MyGate implements ChangeListManagerGate {
    private final ChangeListWorker myWorker;

    private MyGate(ChangeListWorker worker) {
      myWorker = worker;
    }

    @NotNull
    @Override
    public List<LocalChangeList> getListsCopy() {
      return ContainerUtil.map(myWorker.getChangeLists(), LocalChangeList::copy);
    }

    @Nullable
    @Override
    public LocalChangeList findChangeList(final String name) {
      return myWorker.getChangeListCopyByName(name);
    }

    @NotNull
    @Override
    public LocalChangeList addChangeList(@NotNull String name, @Nullable String comment) {
      return myWorker.addChangeList(name, comment, true, null).copy();
    }

    @NotNull
    @Override
    public LocalChangeList findOrCreateList(@NotNull final String name, final String comment) {
      LocalChangeList list = myWorker.getChangeListByName(name);
      if (list == null) {
        list = addChangeList(name, comment);
      }
      return list.copy();
    }

    @Override
    public void editComment(@NotNull final String name, final String comment) {
      myWorker.editComment(name, comment);
    }

    @Override
    public void editName(@NotNull String oldName, @NotNull String newName) {
      myWorker.editName(oldName, newName);
    }

    @Override
    public void setListsToDisappear(@NotNull Collection<String> names) {
      myWorker.setListsToDisappear(names);
    }

    @Override
    public FileStatus getStatus(@NotNull VirtualFile file) {
      return myWorker.getStatus(file);
    }

    @Deprecated
    @Override
    public FileStatus getStatus(@NotNull File file) {
      return myWorker.getStatus(VcsUtil.getFilePath(file));
    }

    @Override
    public FileStatus getStatus(@NotNull FilePath filePath) {
      return myWorker.getStatus(filePath);
    }

    @Override
    public void setDefaultChangeList(@NotNull String list) {
      myWorker.setDefault(list);
    }
  }

  private final Comparator<Pair<Change, String>> CHANGES_AFTER_REVISION_COMPARATOR = (o1, o2) -> {
    String s1 = o1.getFirst().getAfterRevision().getFile().getPresentableUrl();
    String s2 = o2.getFirst().getAfterRevision().getFile().getPresentableUrl();
    return SystemInfo.isFileSystemCaseSensitive ? s1.compareTo(s2) : s1.compareToIgnoreCase(s2);
  };

  @Override
  public String toString() {
    return String.format("ChangeListWorker{myMap=%s}", StringUtil.join(myMap.values(), list -> {
      return String.format("list: %s changes: %s", list.getName(), StringUtil.join(list.getChanges(), ", "));
    }, "\n"));
  }
}
