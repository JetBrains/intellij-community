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
import org.jetbrains.annotations.Contract;
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

  private final Set<ListData> myLists;
  private ListData myDefault;

  private final ChangeListsIndexes myIdx;
  private final ChangesDelta myDelta;
  private final Set<String> myListsToDisappear;

  private final Map<String, OpenTHashSet<Change>> myChangesBeforeUpdateMap = new HashMap<>();

  public ChangeListWorker(@NotNull Project project, @NotNull PlusMinusModify<BaseRevision> deltaListener) {
    myProject = project;
    myLists = new HashSet<>();
    myIdx = new ChangeListsIndexes();

    myDelta = new ChangesDelta(deltaListener);
    myListsToDisappear = ContainerUtil.newLinkedHashSet();

    ensureDefaultListExists();
  }

  private ChangeListWorker(ChangeListWorker worker) {
    myProject = worker.myProject;

    myLists = new HashSet<>();
    myIdx = new ChangeListsIndexes(worker.myIdx);

    myDelta = worker.myDelta;
    myListsToDisappear = ContainerUtil.newLinkedHashSet(worker.myListsToDisappear);

    for (ListData list : worker.myLists) {
      ListData copy = new ListData(list);
      putNewListData(copy);

      if (copy.isDefault) {
        if (myDefault != null) {
          LOG.error("multiple default lists found when copy");
          copy.isDefault = false;
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

    if (!myLists.isEmpty()) {
      myDefault = myLists.iterator().next();
      myDefault.isDefault = true;
    }
    else {
      myDefault = new ListData(null, LocalChangeList.DEFAULT_NAME);
      myDefault.isDefault = true;
      putNewListData(myDefault);
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

    for (ListData list : myLists) {
      for (Change change : list.changes) {
        if (change.isMoved() || change.isRenamed()) {
          moves.putValue(change.getBeforeRevision().getFile(), Pair.create(change, list.name));
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

        ListData list = getDataByName(item.getSecond());
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
    if (name == null) return null;
    return toChangeList(getDataByName(name));
  }

  @Nullable
  public LocalChangeList getChangeListById(@Nullable String id) {
    if (id == null) return null;
    return toChangeList(getDataById(id));
  }

  /**
   * @return previous default list name or null if nothing was done
   */
  @Nullable
  public String setDefault(String name) {
    ListData newDefault = getDataByName(name);
    if (newDefault == null) {
      return null;
    }

    String previousName = myDefault.name;

    myDefault.isDefault = false;
    newDefault.isDefault = true;
    myDefault = newDefault;

    return previousName;
  }

  public boolean setReadOnly(String name, boolean value) {
    ListData list = getDataByName(name);
    if (list != null) {
      list.isReadOnly = value;
    }
    return list != null;
  }

  @NotNull
  public LocalChangeList addChangeList(@NotNull String name, @Nullable String comment, @Nullable String id, @Nullable ChangeListData data) {
    return addChangeList(name, comment, id, data, false);
  }

  @NotNull
  private LocalChangeList addChangeList(@NotNull String name, @Nullable String description, @Nullable String id,
                                        @Nullable ChangeListData data, boolean inUpdate) {
    LocalChangeList existingList = getChangeListByName(name);
    if (existingList != null) {
      LOG.error("Attempt to create duplicate changelist " + name);
      return existingList;
    }

    ListData list = new ListData(id, name);
    list.comment = StringUtil.notNullize(description);
    list.data = data;

    putNewListData(list);

    if (inUpdate) {
      startProcessingChanges(list); // this is executed only when use through GATE
    }
    return toChangeList(list);
  }

  void setChangeLists(@NotNull Collection<LocalChangeListImpl> lists) {
    myDefault = null;
    myLists.clear();
    myIdx.clear();

    for (LocalChangeListImpl list : lists) {
      ListData copy = new ListData(list);
      putNewListData(copy);

      if (list.isDefault()) {
        if (myDefault != null) {
          LOG.error("multiple default lists found");
          copy.isDefault = false;
        }
        else {
          myDefault = copy;
        }
      }

      for (Change change : copy.changes) {
        myIdx.changeAdded(change, null);
      }
    }

    ensureDefaultListExists();
  }

  private void addChangeToList(@NotNull ListData list, @NotNull Change change, VcsKey vcsKey) {
    list.addChange(change);
    myIdx.changeAdded(change, vcsKey);
  }

  public void addChangeToList(@NotNull String name, @NotNull Change change, VcsKey vcsKey) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("[addChangeToList] name: " + name + " change: " + ChangesUtil.getFilePath(change).getPath() +
                " vcs: " + (vcsKey == null ? null : vcsKey.getName()));
    }

    ListData changeList = getDataByName(name);
    if (changeList == null) return;

    addChangeToList(changeList, change, vcsKey);
  }

  public void addChangeToCorrespondingList(@NotNull Change change, VcsKey vcsKey) {
    if (LOG.isDebugEnabled()) {
      final String path = ChangesUtil.getFilePath(change).getPath();
      LOG.debug("[addChangeToCorrespondingList] for change " + path + " type: " + change.getType() +
                " have before revision: " + (change.getBeforeRevision() != null));
    }

    for (ListData list : myLists) {
      OpenTHashSet<Change> changesBeforeUpdate = myChangesBeforeUpdateMap.get(list.name);
      if (changesBeforeUpdate.contains(change)) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("[addChangeToCorrespondingList] matched: " + list.name);
        }
        addChangeToList(list, change, vcsKey);
        return;
      }
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("[addChangeToCorrespondingList] added to default list");
    }
    addChangeToList(myDefault, change, vcsKey);
  }

  public boolean removeChangeList(@NotNull String name) {
    ListData list = getDataByName(name);
    if (list == null) return false;

    if (list.isDefault) {
      LOG.error("Cannot remove default changelist");
      return false;
    }

    myDefault.addChanges(list.changes);

    removeListData(list);
    return true;
  }

  /**
   * @return moved changes and their old changelist
   */
  @Nullable
  public MultiMap<LocalChangeList, Change> moveChangesTo(String name, @NotNull Change[] changes) {
    final ListData targetList = getDataByName(name);
    if (targetList == null) return null;

    final MultiMap<LocalChangeList, Change> result = new MultiMap<>();
    for (ListData sourceList : myLists) {
      if (sourceList.equals(targetList)) continue;

      List<Change> removedChanges = new ArrayList<>();
      for (Change change : changes) {
        ContainerUtil.addIfNotNull(removedChanges, sourceList.changes.get(change));
      }

      if (!removedChanges.isEmpty()) {
        sourceList.removeChanges(removedChanges);
        targetList.addChanges(removedChanges);
        result.putValues(toChangeList(sourceList), removedChanges);
      }
    }
    return result;
  }

  public boolean editName(@NotNull String fromName, @NotNull String toName) {
    if (fromName.equals(toName)) return false;
    if (getDataByName(toName) != null) return false;

    final ListData list = getDataByName(fromName);
    if (list == null || list.isReadOnly) return false;

    list.name = toName;

    OpenTHashSet<Change> changesBeforeUpdateFrom = myChangesBeforeUpdateMap.remove(fromName);
    OpenTHashSet<Change> changesBeforeUpdateTo = myChangesBeforeUpdateMap.put(toName, changesBeforeUpdateFrom);
    LOG.assertTrue(changesBeforeUpdateTo == null, "old changes for new changelist name found during rename");

    return true;
  }

  @Nullable
  public String editComment(@NotNull String name, @NotNull String newComment) {
    final ListData list = getDataByName(name);
    if (list == null) return null;

    final String oldComment = list.comment;
    if (!Comparing.equal(oldComment, newComment)) {
      list.comment = newComment;
    }
    return oldComment;
  }

  public boolean isEmpty() {
    return myLists.isEmpty();
  }

  @NotNull
  public LocalChangeList getDefaultList() {
    return toChangeList(myDefault);
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
    for (ListData list : myLists) {
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
    for (ListData list : myLists) {
      final List<Change> removed = new ArrayList<>();
      final List<Change> added = new ArrayList<>();
      boolean wasChanged = doneProcessingChanges(list, removed, added);

      LocalChangeListImpl changeList = toChangeList(list);
      if (wasChanged) {
        changedLists.add(changeList);
      }
      if (!removed.isEmpty()) {
        removedChanges.put(changeList, removed);
      }
      if (!added.isEmpty()) {
        addedChanges.put(changeList, added);
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
      final ListData list = getDataByName(name);
      if (list != null && list.changes.isEmpty() && !list.isReadOnly && !list.isDefault) {
        removeChangeList(name);
      }
    }
    myListsToDisappear.clear();

    myChangesBeforeUpdateMap.clear();
  }

  private void startProcessingChanges(@NotNull ListData list) {
    OpenTHashSet<Change> changesBeforeUpdate = new OpenTHashSet<>((Collection<Change>)list.changes);
    myChangesBeforeUpdateMap.put(list.name, changesBeforeUpdate);
  }

  @NotNull
  private List<Change> removeChangesUnderScope(@NotNull ListData list, @Nullable VcsModifiableDirtyScope scope) {
    List<Change> removed = new ArrayList<>();
    for (Change change : list.changes) {
      ContentRevision before = change.getBeforeRevision();
      ContentRevision after = change.getAfterRevision();
      boolean isUnderScope = scope == null ||
                             before != null && scope.belongsTo(before.getFile()) ||
                             after != null && scope.belongsTo(after.getFile()) ||
                             isIgnoredChange(before, after, myProject);
      if (isUnderScope) {
        removed.add(change);
      }
    }

    list.removeChanges(removed);

    for (Change change : removed) {
      myIdx.changeRemoved(change);
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

  private boolean doneProcessingChanges(@NotNull ListData list,
                                        @NotNull List<Change> removedChanges,
                                        @NotNull List<Change> addedChanges) {
    OpenTHashSet<Change> changesBeforeUpdate = myChangesBeforeUpdateMap.get(list.name);

    for (Change newChange : list.changes) {
      Change oldChange = findOldChange(changesBeforeUpdate, newChange);
      if (oldChange == null) {
        addedChanges.add(newChange);
      }
    }

    removedChanges.addAll(changesBeforeUpdate);
    removedChanges.removeAll(list.changes);

    return list.changes.size() != changesBeforeUpdate.size() ||
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
    return ContainerUtil.map(myLists, this::toChangeList);
  }

  @NotNull
  public List<File> getAffectedPaths() {
    final Set<FilePath> set = myIdx.getAffectedPaths();
    final List<File> result = new ArrayList<>(set.size());
    for (FilePath path : set) {
      result.add(path.getIOFile());
    }
    return result;
  }

  @NotNull
  public List<VirtualFile> getAffectedFiles() {
    final Set<VirtualFile> result = ContainerUtil.newLinkedHashSet();
    for (ListData list : myLists) {
      for (Change change : list.changes) {
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

    Pair<ListData, Change> pair = getChangeAndListByPath(filePath);
    if (pair == null) return null;

    return toChangeList(pair.first);
  }

  public void removeRegisteredChangeFor(@Nullable FilePath filePath) {
    myIdx.remove(filePath);

    Pair<ListData, Change> pair = getChangeAndListByPath(filePath);
    if (pair == null) return;

    ListData list = pair.first;
    Change change = pair.second;

    list.removeChange(change);
  }

  @Nullable
  public Change getChangeForPath(@Nullable FilePath filePath) {
    Pair<ListData, Change> pair = getChangeAndListByPath(filePath);
    if (pair == null) return null;

    return pair.second;
  }

  @Nullable
  private Pair<ListData, Change> getChangeAndListByPath(@Nullable FilePath filePath) {
    if (filePath == null) return null;
    for (ListData list : myLists) {
      for (Change change : list.changes) {
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
    for (ListData list : myLists) {
      changes.addAll(list.changes);
    }
    return changes;
  }

  public int getChangeListsNumber() {
    return myLists.size();
  }

  @NotNull
  public Collection<LocalChangeList> getInvolvedLists(@NotNull Collection<Change> changes) {
    List<LocalChangeList> result = new ArrayList<>();

    for (ListData list : myLists) {
      for (Change change : changes) {
        if (list.changes.contains(change)) {
          result.add(toChangeList(list));
          break;
        }
      }
    }

    return result;
  }

  @Nullable
  public LocalChangeList getChangeListForChange(final Change change) {
    for (ListData list : myLists) {
      if (list.changes.contains(change)) return toChangeList(list);
    }
    return null;
  }

  @Nullable
  public LocalChangeList getChangeListIfOnlyOne(@Nullable Change[] changes) {
    if (changes == null || changes.length == 0) {
      return null;
    }

    final Change first = changes[0];

    for (ListData list : myLists) {
      if (list.changes.contains(first)) {
        // must contain all other
        for (int i = 1; i < changes.length; i++) {
          final Change change = changes[i];
          if (!list.changes.contains(change)) {
            return null;
          }
        }
        return toChangeList(list);
      }
    }
    return null;
  }

  public ThreeState haveChangesUnder(@NotNull VirtualFile virtualFile) {
    FilePath dir = VcsUtil.getFilePath(virtualFile);
    return myIdx.haveChangesUnder(dir);
  }

  @NotNull
  public List<Change> getChangesIn(@NotNull FilePath dirPath) {
    List<Change> changes = new ArrayList<>();
    for (ListData list : myLists) {
      for (Change change : list.changes) {
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


  private void putNewListData(@NotNull ListData list) {
    if (getChangeListByName(list.name) != null ||
        getChangeListById(list.id) != null) {
      LOG.error(String.format("Attempt to create duplicate changelist: %s - %s", list.name, list.id));
      return;
    }

    myLists.add(list);
  }

  private void removeListData(@NotNull ListData list) {
    myLists.remove(list);
  }

  private ListData getDataById(@NotNull String id) {
    return ContainerUtil.find(myLists, list -> list.id.equals(id));
  }

  private ListData getDataByName(@NotNull String name) {
    return ContainerUtil.find(myLists, list -> list.name.equals(name));
  }

  @Contract("!null -> !null; null -> null")
  private LocalChangeListImpl toChangeList(@Nullable ListData data) {
    if (data == null) return null;

    return new LocalChangeListImpl.Builder(myProject, data.name)
      .setId(data.id)
      .setComment(data.comment)
      .setChangesCollection(data.getReadOnlyChangesCopy())
      .setData(data.data)
      .setDefault(data.isDefault)
      .setReadOnly(data.isReadOnly)
      .build();
  }

  private static class ListData {
    @NotNull public final String id;

    @NotNull public String name;
    @NotNull public String comment = "";
    @Nullable public ChangeListData data;

    public boolean isDefault = false;
    public boolean isReadOnly = false; // read-only lists cannot be removed or renamed

    public final OpenTHashSet<Change> changes = new OpenTHashSet<>();
    public Set<Change> readOnlyChangesCache = null;

    public ListData(@Nullable String id, @NotNull String name) {
      this.id = id != null ? id : LocalChangeListImpl.generateChangelistId();
      this.name = name;
    }

    public ListData(@NotNull LocalChangeListImpl list) {
      this.id = list.getId();
      this.name = list.getName();
      this.comment = list.getComment();
      this.data = list.getData();
      this.isDefault = list.isDefault();
      this.isReadOnly = list.isReadOnly();
      this.changes.addAll(list.getChanges());
    }

    public ListData(@NotNull ListData list) {
      this.id = list.id;
      this.name = list.name;
      this.comment = list.comment;
      this.data = list.data;
      this.isDefault = list.isDefault;
      this.isReadOnly = list.isReadOnly;
      this.changes.addAll(list.changes);
    }

    public Set<Change> getReadOnlyChangesCopy() {
      if (readOnlyChangesCache == null) {
        readOnlyChangesCache = new HashSet<>(changes);
      }
      return readOnlyChangesCache;
    }

    public void addChange(@NotNull Change change) {
      readOnlyChangesCache = null;
      changes.add(change);
    }

    public void removeChange(@NotNull Change change) {
      readOnlyChangesCache = null;
      changes.remove(change);
    }

    public void addChanges(@NotNull Collection<Change> list) {
      readOnlyChangesCache = null;
      changes.addAll(list);
    }

    public void removeChanges(@NotNull Collection<Change> list) {
      readOnlyChangesCache = null;
      changes.removeAll(list);
    }
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
      return myWorker.getChangeLists();
    }

    @Nullable
    @Override
    public LocalChangeList findChangeList(final String name) {
      return myWorker.getChangeListByName(name);
    }

    @NotNull
    @Override
    public LocalChangeList addChangeList(@NotNull String name, @Nullable String comment) {
      return myWorker.addChangeList(name, comment, null, null, true);
    }

    @NotNull
    @Override
    public LocalChangeList findOrCreateList(@NotNull final String name, final String comment) {
      LocalChangeList list = myWorker.getChangeListByName(name);
      if (list != null) return list;
      return addChangeList(name, comment);
    }

    @Override
    public void editComment(@NotNull final String name, final String comment) {
      myWorker.editComment(name, StringUtil.notNullize(comment));
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
    return String.format("ChangeListWorker{myMap=%s}", StringUtil.join(myLists, list -> {
      return String.format("list: %s changes: %s", list.name, StringUtil.join(list.changes, ", "));
    }, "\n"));
  }
}
