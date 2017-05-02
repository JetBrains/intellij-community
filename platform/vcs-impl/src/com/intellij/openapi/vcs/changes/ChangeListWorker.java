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
import com.intellij.util.IncorrectOperationException;
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
public class ChangeListWorker implements ChangeListsWriteOperations {
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangeListWorker");

  private final Project myProject;
  private final Map<String, LocalChangeListImpl> myMap;
  private LocalChangeListImpl myDefault;

  private final ChangeListsIndexes myIdx;
  private final ChangesDelta myDelta;
  private final Set<String> myListsToDisappear;

  private final Map<LocalChangeListImpl, OpenTHashSet<Change>> myChangesBeforeUpdateMap = new HashMap<>();

  public ChangeListWorker(final Project project, final PlusMinusModify<BaseRevision> deltaListener) {
    myProject = project;
    myMap = new LinkedHashMap<>();
    myIdx = new ChangeListsIndexes();

    myDelta = new ChangesDelta(deltaListener);
    myListsToDisappear = ContainerUtil.newLinkedHashSet();
  }

  private ChangeListWorker(final ChangeListWorker worker) {
    myProject = worker.myProject;
    myMap = new LinkedHashMap<>();
    myIdx = new ChangeListsIndexes(worker.myIdx);
    myDelta = worker.myDelta;
    myListsToDisappear = ContainerUtil.newLinkedHashSet(worker.myListsToDisappear);

    LocalChangeListImpl defaultList = null;
    for (LocalChangeListImpl changeList : worker.myMap.values()) {
      final LocalChangeListImpl copy = changeList.copy();
      final String changeListName = copy.getName();
      myMap.put(changeListName, copy);
      if (copy.isDefault()) {
        defaultList = copy;
      }
    }
    if (defaultList == null) {
      LOG.info("default list not found when copy");
      defaultList = myMap.get(worker.getDefaultListName());
    }

    if (defaultList == null) {
      LOG.info("default list not found when copy in original object too");
      if (!myMap.isEmpty()) {
        defaultList = myMap.values().iterator().next();
      } else {
        // can be when there's no vcs configured
        ///LOG.error("no changelists at all");
      }
    }
    myDefault = defaultList;
  }

  public void onAfterWorkerSwitch(@NotNull final ChangeListWorker previous) {
    boolean somethingChanged = myDelta.step(previous.myIdx, myIdx);
    somethingChanged |= checkForMultipleCopiesNotMove();

    if (somethingChanged) {
      FileStatusManager.getInstance(myProject).fileStatusesChanged();
    }
  }

  private boolean checkForMultipleCopiesNotMove() {
    boolean somethingChanged = false;
    final MultiMap<FilePath, Pair<Change, String>> moves = new MultiMap<FilePath, Pair<Change, String>>() {
      @NotNull
      protected Collection<Pair<Change, String>> createCollection() {
        return new LinkedList<>();
      }
    };

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
      copies.sort(MyChangesAfterRevisionComparator.getInstance());
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

  public boolean findListByName(@NotNull final String name) {
    return myMap.containsKey(name);
  }

  @Nullable
  public LocalChangeList getCopyByName(final String name) {
    return myMap.get(name);
  }

  @Nullable
  public LocalChangeList getChangeList(String id) {
    for (LocalChangeList changeList : myMap.values()) {
      if (changeList.getId().equals(id)) {
        return changeList.copy();
      }
    }
    return null;
  }

  /**
   * @return if list with name exists, return previous default list name or null of there wasn't previous
   */
  @Nullable
  public String setDefault(final String name) {
    final LocalChangeListImpl newDefault = myMap.get(name);
    if (newDefault == null) {
      return null;
    }
    String previousName = null;
    if (myDefault != null) {
      myDefault.setDefault(false);
      previousName = myDefault.getName();
    }

    newDefault.setDefault(true);
    myDefault = newDefault;

    return previousName;
  }

  public boolean setReadOnly(final String name, final boolean value) {
    final LocalChangeList list = myMap.get(name);
    if (list != null) {
      list.setReadOnly(value);
    }
    return list != null;
  }

  public LocalChangeList addChangeList(@NotNull final String name, @Nullable final String comment, @Nullable Object data) {
    return addChangeList(null, name, comment, false, data);
  }

  LocalChangeList addChangeList(String id, @NotNull final String name, @Nullable final String description, final boolean inUpdate,
                                @Nullable Object data) {
    final boolean contains = myMap.containsKey(name);
    LOG.assertTrue(!contains, "Attempt to create duplicate changelist " + name);
    final LocalChangeListImpl newList = LocalChangeListImpl.createEmptyChangeListImpl(myProject, name, id);
    newList.setData(data);

    if (description != null) {
      newList.setComment(description);
    }
    myMap.put(name, newList);
    if (inUpdate) {
      // scope is not important: nothing had been added jet, nothing to move to "old state" members
      startProcessingChanges(newList, null); // this is executed only when use through GATE
    }
    return newList.copy();
  }

  public boolean addChangeToList(@NotNull final String name, final Change change, final VcsKey vcsKey) {
    LOG.debug("[addChangeToList] name: " + name + " change: " + ChangesUtil.getFilePath(change).getPath() + " vcs: " +
              (vcsKey == null ? null : vcsKey.getName()));
    final LocalChangeListImpl changeList = myMap.get(name);
    if (changeList != null) {
      changeList.addChange(change);
      myIdx.changeAdded(change, vcsKey);
    }
    return changeList != null;
  }

  public void addChangeToCorrespondingList(@NotNull Change change, final VcsKey vcsKey) {
    final String path = ChangesUtil.getFilePath(change).getPath();
    LOG.debug("[addChangeToCorrespondingList] for change " + path  + " type: " + change.getType() + " have before revision: " + (change.getBeforeRevision() != null));
    assert myDefault != null;
    for (LocalChangeListImpl list : myMap.values()) {
      OpenTHashSet<Change> changesBeforeUpdate = myChangesBeforeUpdateMap.get(list);
      if (changesBeforeUpdate.contains(change)) {
        LOG.debug("[addChangeToCorrespondingList] matched: " + list.getName());
        list.addChange(change);
        myIdx.changeAdded(change, vcsKey);
        return;
      }
    }
    LOG.debug("[addChangeToCorrespondingList] added to default list");
    myDefault.addChange(change);
    myIdx.changeAdded(change, vcsKey);
  }

  public boolean removeChangeList(@NotNull String name) {
    final LocalChangeList list = myMap.get(name);
    if (list == null) {
      return false;
    }
    if (list.isDefault()) {
      throw new RuntimeException(new IncorrectOperationException("Cannot remove default changelist"));
    }
    final String listName = list.getName();

    for (Change change : list.getChanges()) {
      myDefault.addChange(change);
    }

    myMap.remove(listName);
    return true;
  }

  @Nullable
  public MultiMap<LocalChangeList, Change> moveChangesTo(final String name, final Change[] changes) {
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

  public boolean editName(@NotNull final String fromName, @NotNull final String toName) {
    if (fromName.equals(toName)) return false;
    final LocalChangeListImpl list = myMap.get(fromName);
    final boolean canEdit = list != null && (!list.isReadOnly());
    if (canEdit) {
      list.setName(toName);
      myMap.remove(fromName);
      myMap.put(toName, list);
    }
    return canEdit;
  }

  @Nullable
  public String editComment(@NotNull final String fromName, final String newComment) {
    final LocalChangeListImpl list = myMap.get(fromName);
    if (list != null) {
      final String oldComment = list.getComment();
      if (!Comparing.equal(oldComment, newComment)) {
        list.setComment(newComment);
      }
      return oldComment;
    }
    return null;
  }

  public boolean isEmpty() {
    return myMap.isEmpty();
  }

  @Nullable
  public LocalChangeList getDefaultListCopy() {
    return myDefault == null ? null : myDefault.copy();
  }

  public Project getProject() {
    return myProject;
  }

  // called NOT under ChangeListManagerImpl lock
  public void notifyStartProcessingChanges(final VcsModifiableDirtyScope scope) {
    final Collection<Change> oldChanges = new ArrayList<>();
    for (LocalChangeListImpl list : myMap.values()) {
      final Collection<Change> affectedChanges = startProcessingChanges(list, scope);
      if (!affectedChanges.isEmpty()) {
        oldChanges.addAll(affectedChanges);
      }
    }
    for (Change change : oldChanges) {
      myIdx.changeRemoved(change);
    }
    // scope should be modified for correct moves tracking
    correctScopeForMoves(scope, oldChanges);
  }

  private static void correctScopeForMoves(final VcsModifiableDirtyScope scope, final Collection<Change> changes) {
    if (scope == null) return;
    for (Change change : changes) {
      if (change.isMoved() || change.isRenamed()) {
        scope.addDirtyFile(change.getBeforeRevision().getFile());
        scope.addDirtyFile(change.getAfterRevision().getFile());
      }
    }
  }

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

  private Collection<Change> startProcessingChanges(@NotNull LocalChangeListImpl list, @Nullable final VcsDirtyScope scope) {
    OpenTHashSet<Change> changesBeforeUpdate = new OpenTHashSet<>(list.getChanges());
    myChangesBeforeUpdateMap.put(list, changesBeforeUpdate);

    final Collection<Change> result = new ArrayList<>();
    for (Change oldBoy : changesBeforeUpdate) {
      final ContentRevision before = oldBoy.getBeforeRevision();
      final ContentRevision after = oldBoy.getAfterRevision();
      if (scope == null || before != null && scope.belongsTo(before.getFile()) || after != null && scope.belongsTo(after.getFile())
          || isIgnoredChange(oldBoy, myProject)) {
        result.add(oldBoy);
        list.removeChange(oldBoy);
      }
    }
    return result;
  }

  private static boolean isIgnoredChange(@NotNull Change change, @NotNull Project project) {
    boolean beforeRevIgnored = change.getBeforeRevision() == null || isIgnoredRevision(change.getBeforeRevision(), project);
    boolean afterRevIgnored = change.getAfterRevision() == null || isIgnoredRevision(change.getAfterRevision(), project);
    return beforeRevIgnored && afterRevIgnored;
  }

  private static boolean isIgnoredRevision(final @NotNull ContentRevision revision, final @NotNull Project project) {
    return ReadAction.compute(() -> {
      if (project.isDisposed()) {
        return false;
      }
      VirtualFile vFile = revision.getFile().getVirtualFile();
      return vFile != null && ProjectLevelVcsManager.getInstance(project).isIgnored(vFile);
    });
  }

  private boolean doneProcessingChanges(@NotNull LocalChangeListImpl list, List<Change> removedChanges, List<Change> addedChanges) {
    OpenTHashSet<Change> changesBeforeUpdate = myChangesBeforeUpdateMap.get(list);
    Set<Change> changes = list.getChanges();
    boolean changesDetected = (changes.size() != changesBeforeUpdate.size());

    for (Change newChange : changes) {
      Change oldChange = findOldChange(changesBeforeUpdate, newChange);
      if (oldChange == null) {
        addedChanges.add(newChange);
      }
    }
    changesDetected |= (!addedChanges.isEmpty());
    final List<Change> removed = new ArrayList<>(changesBeforeUpdate);
    // since there are SAME objects...
    removed.removeAll(changes);
    removedChanges.addAll(removed);
    changesDetected = changesDetected || (!removedChanges.isEmpty());

    return changesDetected;
  }

  @Nullable
  private static Change findOldChange(OpenTHashSet<Change> changesBeforeUpdate, Change newChange) {
    Change oldChange = changesBeforeUpdate.get(newChange);
    if (oldChange != null && sameBeforeRevision(oldChange, newChange) &&
        newChange.getFileStatus().equals(oldChange.getFileStatus())) {
      return oldChange;
    }
    return null;
  }

  private static boolean sameBeforeRevision(final Change change1, final Change change2) {
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
  public List<LocalChangeList> getListsCopy() {
    final List<LocalChangeList> result = new ArrayList<>();
    for (LocalChangeList list : myMap.values()) {
      result.add(list.copy());
    }
    return result;
  }

  public String getDefaultListName() {
    return myDefault == null ? null : myDefault.getName();
  }

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
  public LocalChangeList getListCopy(@NotNull final VirtualFile file) {
    FilePath filePath = VcsUtil.getFilePath(file);
    for (LocalChangeList list : myMap.values()) {
      for (Change change : list.getChanges()) {
        if (change.getAfterRevision() != null && Comparing.equal(change.getAfterRevision().getFile(), filePath) ||
            change.getBeforeRevision() != null && Comparing.equal(change.getBeforeRevision().getFile(), filePath)) {
          return list.copy();
        }
      }
    }
    return null;
  }

  @Nullable
  public Change getChangeForPath(final FilePath file) {
    for (LocalChangeList list : myMap.values()) {
      for (Change change : list.getChanges()) {
        final ContentRevision afterRevision = change.getAfterRevision();
        if (afterRevision != null && afterRevision.getFile().equals(file)) {
          return change;
        }
        final ContentRevision beforeRevision = change.getBeforeRevision();
        if (beforeRevision != null && beforeRevision.getFile().equals(file)) {
          return change;
        }
      }
    }
    return null;
  }

  public FileStatus getStatus(@NotNull VirtualFile file) {
    return myIdx.getStatus(file);
  }

  public FileStatus getStatus(@NotNull FilePath file) {
    return myIdx.getStatus(file);
  }

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
      LocalChangeList copy = list.copy();
      for (Change change : list.getChanges()) {
        internalMap.put(change, copy);
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
  public LocalChangeList listForChange(final Change change) {
    for (LocalChangeList list : myMap.values()) {
      if (list.getChanges().contains(change)) return list.copy();
    }
    return null;
  }

  @Nullable
  public String listNameIfOnlyOne(final @Nullable Change[] changes) {
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
        return list.getName();
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
  public Collection<Change> getChangesIn(@NotNull FilePath dirPath) {
    List<Change> changes = new ArrayList<>();
    for (ChangeList list : myMap.values()) {
      for (Change change : list.getChanges()) {
        final ContentRevision afterRevision = change.getAfterRevision();
        if (afterRevision != null && afterRevision.getFile().isUnder(dirPath, false)) {
          changes.add(change);
          continue;
        }

        final ContentRevision beforeRevision = change.getBeforeRevision();
        if (beforeRevision != null && beforeRevision.getFile().isUnder(dirPath, false)) {
          changes.add(change);
        }
      }
    }
    return changes;
  }

  @Nullable
  VcsKey getVcsFor(@NotNull Change change) {
    return myIdx.getVcsFor(change);
  }

  void setListsToDisappear(final Collection<String> names) {
    myListsToDisappear.addAll(names);
  }

  @NotNull
  ChangeListManagerGate createSelfGate() {
    return new MyGate(this);
  }

  private static class MyGate implements ChangeListManagerGate {
    private final ChangeListWorker myWorker;

    private MyGate(final ChangeListWorker worker) {
      myWorker = worker;
    }

    @Override
    public List<LocalChangeList> getListsCopy() {
      return myWorker.getListsCopy();
    }

    @Nullable
    @Override
    public LocalChangeList findChangeList(final String name) {
      return myWorker.getCopyByName(name);
    }

    @Override
    public LocalChangeList addChangeList(final String name, final String comment) {
      return myWorker.addChangeList(null, name, comment, true, null);
    }

    @Override
    public LocalChangeList findOrCreateList(final String name, final String comment) {
      LocalChangeList list = myWorker.getCopyByName(name);
      if (list == null) {
        list = addChangeList(name, comment);
      }
      return list;
    }

    @Override
    public void editComment(final String name, final String comment) {
      myWorker.editComment(name, comment);
    }

    @Override
    public void editName(String oldName, String newName) {
      myWorker.editName(oldName, newName);
    }

    @Override
    public void setListsToDisappear(final Collection<String> names) {
      myWorker.setListsToDisappear(names);
    }

    @Override
    public FileStatus getStatus(VirtualFile file) {
      return myWorker.getStatus(file);
    }

    @Deprecated
    @Override
    public FileStatus getStatus(File file) {
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

  public void removeRegisteredChangeFor(FilePath path) {
    myIdx.remove(path);

    for (LocalChangeListImpl list : myMap.values()) {
      for (Change change : list.getChanges()) {
        final ContentRevision afterRevision = change.getAfterRevision();
        if (afterRevision != null && afterRevision.getFile().equals(path)) {
          list.removeChange(change);
          return;
        }
        final ContentRevision beforeRevision = change.getBeforeRevision();
        if (beforeRevision != null && beforeRevision.getFile().equals(path)) {
          list.removeChange(change);
          return;
        }
      }
    }
  }

  // assumes after revisions are all not null
  private static class MyChangesAfterRevisionComparator implements Comparator<Pair<Change, String>> {
    private static final MyChangesAfterRevisionComparator ourInstance = new MyChangesAfterRevisionComparator();

    public static MyChangesAfterRevisionComparator getInstance() {
      return ourInstance;
    }

    public int compare(final Pair<Change, String> o1, final Pair<Change, String> o2) {
      final String s1 = o1.getFirst().getAfterRevision().getFile().getPresentableUrl();
      final String s2 = o2.getFirst().getAfterRevision().getFile().getPresentableUrl();
      return SystemInfo.isFileSystemCaseSensitive ? s1.compareTo(s2) : s1.compareToIgnoreCase(s2);
    }
  }

  @Override
  public String toString() {
    return String.format("ChangeListWorker{myMap=%s}", StringUtil.join(myMap.values(), list -> {
      return String.format("list: %s changes: %s", list.getName(), StringUtil.join(list.getChanges(), ", "));
    }, "\n"));
  }
}
