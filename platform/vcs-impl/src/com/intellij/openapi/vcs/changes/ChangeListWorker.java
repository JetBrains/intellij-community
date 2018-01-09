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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ui.PlusMinusModify;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BeforeAfter;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.OpenTHashSet;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** should work under _external_ lock
 * just logic here: do modifications to group of change lists
 */
public class ChangeListWorker {
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangeListWorker");
  @NotNull private final Project myProject;
  @NotNull private final DelayedNotificator myDelayedNotificator;
  private final boolean myMainWorker;

  private final Set<ListData> myLists = new HashSet<>();
  private ListData myDefault;

  private final Map<Change, ListData> myChangeMappings = new HashMap<>();
  private final Map<FilePath, PartialChangeTracker> myPartialChangeTrackers = new HashMap<>();

  private final ChangeListsIndexes myIdx;

  @Nullable private Map<ListData, Set<Change>> myReadOnlyChangesCache = null;
  private final AtomicBoolean myReadOnlyChangesCacheInvalidated = new AtomicBoolean(false);

  public ChangeListWorker(@NotNull Project project, @NotNull DelayedNotificator delayedNotificator) {
    myProject = project;
    myDelayedNotificator = delayedNotificator;
    myMainWorker = true;

    myIdx = new ChangeListsIndexes();

    ensureDefaultListExists();
  }

  private ChangeListWorker(@NotNull ChangeListWorker worker) {
    myProject = worker.myProject;
    myDelayedNotificator = worker.myDelayedNotificator;
    myMainWorker = false;

    myIdx = new ChangeListsIndexes(worker.myIdx);

    Map<ListData, ListData> listMapping = copyListsDataFrom(worker.myLists);

    worker.myChangeMappings.forEach((change, oldList) -> {
      ListData newList = notNullList(listMapping.get(oldList));
      myChangeMappings.put(change, newList);
    });

    myPartialChangeTrackers.putAll(worker.myPartialChangeTrackers);
  }

  @NotNull
  private Map<ListData, ListData> copyListsDataFrom(@NotNull Collection<ListData> lists) {
    ListData oldDefault = myDefault;
    List<String> oldIds = ContainerUtil.map(myLists, list -> list.id);

    myLists.clear();
    myDefault = null;

    Map<ListData, ListData> listMapping = new HashMap<>();
    for (ListData oldList : lists) {
      ListData newList = new ListData(oldList);
      if (newList.isDefault && myDefault != null) {
        LOG.error("multiple default lists found when copy");
        newList.isDefault = false;
      }

      newList = putNewListData(newList);
      if (newList.isDefault) myDefault = newList;
      listMapping.put(oldList, newList);
    }

    ensureDefaultListExists();


    if (myMainWorker) {
      if (!oldDefault.id.equals(myDefault.id)) {
        fireDefaultListChanged(oldDefault.id, myDefault.id);
      }

      HashSet<String> removedListIds = new HashSet<>(oldIds);
      for (ListData list : myLists) {
        removedListIds.remove(list.id);
      }

      for (String listId : removedListIds) {
        fireChangeListRemoved(listId);
      }
    }


    myReadOnlyChangesCache = null;

    return listMapping;
  }

  private void ensureDefaultListExists() {
    if (myDefault != null) return;

    if (myLists.isEmpty()) {
      putNewListData(new ListData(null, LocalChangeList.DEFAULT_NAME));
    }

    myDefault = myLists.iterator().next();
    myDefault.isDefault = true;
  }

  public ChangeListWorker copy() {
    return new ChangeListWorker(this);
  }


  public void registerChangeTracker(@NotNull FilePath filePath, @NotNull PartialChangeTracker tracker) {
    if (myPartialChangeTrackers.containsKey(filePath)) {
      LOG.error(String.format("Attempt to register duplicate trackers: %s; old: %s; new: %s",
                              filePath, myPartialChangeTrackers.get(filePath), tracker));
      return;
    }

    myPartialChangeTrackers.put(filePath, tracker);

    tracker.initChangeTracking(myDefault.id, ContainerUtil.map(myLists, list -> list.id));

    Change change = getChangeForAfterPath(filePath);
    if (change != null) {
      removeChangeMapping(change);
    }
  }

  public void unregisterChangeTracker(@NotNull FilePath filePath, @NotNull PartialChangeTracker tracker) {
    PartialChangeTracker oldTracker = myPartialChangeTrackers.remove(filePath);
    if (!Comparing.equal(oldTracker, tracker)) {
      LOG.error(String.format("Wrong tracker removed: %s; expected: %s; passed: %s", filePath, oldTracker, tracker));
    }

    Change change = getChangeForAfterPath(filePath);
    if (change != null) {
      putChangeMapping(change, getMainList(oldTracker));
    }
  }

  @NotNull
  private ListData getMainList(@Nullable PartialChangeTracker oldTracker) {
    if (oldTracker == null) return myDefault;
    List<String> changelistIds = oldTracker.getAffectedChangeListsIds();
    if (changelistIds.size() == 1) {
      ListData list = getDataByIdVerify(changelistIds.get(0));
      if (list != null) return list;
    }
    return myDefault;
  }


  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public LocalChangeList getDefaultList() {
    return toChangeList(myDefault);
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

  @NotNull
  public List<LocalChangeList> getChangeLists() {
    return ContainerUtil.map(myLists, this::toChangeList);
  }

  public int getChangeListsNumber() {
    return myLists.size();
  }


  @Nullable
  public Change getChangeForPath(@Nullable FilePath filePath) {
    if (filePath == null) return null;
    for (Change change : myIdx.getChanges()) {
      ContentRevision before = change.getBeforeRevision();
      ContentRevision after = change.getAfterRevision();
      if (before != null && before.getFile().equals(filePath) ||
          after != null && after.getFile().equals(filePath)) {
        return change;
      }
    }
    return null;
  }

  @Nullable
  private Change getChangeForAfterPath(@Nullable FilePath filePath) {
    if (filePath == null) return null;
    for (Change change : myIdx.getChanges()) {
      ContentRevision after = change.getAfterRevision();
      if (after != null && after.getFile().equals(filePath)) {
        return change;
      }
    }
    return null;
  }

  @NotNull
  public Collection<Change> getAllChanges() {
    return new ArrayList<>(myIdx.getChanges());
  }

  @NotNull
  public List<LocalChangeList> getAffectedLists(@NotNull Collection<Change> changes) {
    return ContainerUtil.map(getAffectedListsData(changes), this::toChangeList);
  }

  @NotNull
  public List<LocalChangeList> getAffectedLists(@NotNull Change change) {
    return getAffectedLists(Collections.singletonList(change));
  }

  @NotNull
  private List<ListData> getAffectedListsData(@NotNull Collection<Change> changes) {
    Set<ListData> result = new HashSet<>();

    for (Change change : changes) {
      ListData list = myChangeMappings.get(change);
      if (list != null) {
        result.add(list);
      }
      else {
        PartialChangeTracker tracker = getChangeTrackerFor(change);
        if (tracker != null) {
          Set<ListData> affectedLists = getAffectedLists(tracker);
          if (change instanceof ChangeListChange) {
            String changeListId = ((ChangeListChange)change).getChangeListId();
            ContainerUtil.addIfNotNull(result, ContainerUtil.find(affectedLists, partialList -> partialList.id.equals(changeListId)));
          }
          else {
            result.addAll(affectedLists);
          }
        }
      }
    }

    return new ArrayList<>(result);
  }

  @NotNull
  private List<Change> getChangesIn(@NotNull ListData data) {
    List<Change> changes = new ArrayList<>();

    for (Change change : myIdx.getChanges()) {
      ListData list = myChangeMappings.get(change);
      if (list != null) {
        if (list == data) {
          changes.add(change);
        }
      }
      else {
        PartialChangeTracker tracker = getChangeTrackerFor(change);
        if (tracker != null && tracker.getAffectedChangeListsIds().contains(data.id)) {
          changes.add(change);
        }
      }
    }

    return changes;
  }

  @NotNull
  private Map<ListData, Set<Change>> getChangesMapping() {
    Map<ListData, Set<Change>> map = new HashMap<>();

    for (Change change : myIdx.getChanges()) {
      ListData list = myChangeMappings.get(change);
      if (list != null) {
        Set<Change> listChanges = map.computeIfAbsent(list, key -> new HashSet<>());
        listChanges.add(change);
      }
      else {
        PartialChangeTracker tracker = getChangeTrackerFor(change);
        if (tracker != null) {
          Set<ListData> lists = getAffectedLists(tracker);
          for (ListData partialList : lists) {
            Set<Change> listChanges = map.computeIfAbsent(partialList, key -> new HashSet<>());
            listChanges.add(toChangeListChange(change, partialList));
          }
        }
      }
    }

    return map;
  }


  @NotNull
  public List<File> getAffectedPaths() {
    return ContainerUtil.map(myIdx.getAffectedPaths(), FilePath::getIOFile);
  }

  @NotNull
  public List<VirtualFile> getAffectedFiles() {
    return ContainerUtil.mapNotNull(myIdx.getAffectedPaths(), FilePath::getVirtualFile);
  }

  public ThreeState haveChangesUnder(@NotNull VirtualFile virtualFile) {
    FilePath dir = VcsUtil.getFilePath(virtualFile);
    return myIdx.haveChangesUnder(dir);
  }

  @NotNull
  public List<Change> getChangesUnder(@NotNull FilePath dirPath) {
    List<Change> changes = new ArrayList<>();
    for (Change change : myIdx.getChanges()) {
      ContentRevision after = change.getAfterRevision();
      ContentRevision before = change.getBeforeRevision();
      if (after != null && after.getFile().isUnder(dirPath, false) ||
          before != null && before.getFile().isUnder(dirPath, false)) {
        changes.add(change);
      }
    }
    return changes;
  }

  @Nullable
  public VcsKey getVcsFor(@NotNull Change change) {
    return myIdx.getVcsFor(change);
  }

  @Nullable
  public FileStatus getStatus(@NotNull VirtualFile file) {
    return myIdx.getStatus(file);
  }

  @Nullable
  public FileStatus getStatus(@NotNull FilePath file) {
    return myIdx.getStatus(file);
  }


  public boolean setDefaultList(String name) {
    ListData newDefault = getDataByName(name);
    if (newDefault == null || newDefault.isDefault) return false;

    ListData oldDefault = myDefault;

    myDefault.isDefault = false;
    newDefault.isDefault = true;
    myDefault = newDefault;

    fireDefaultListChanged(oldDefault.id, newDefault.id);
    fireChangeListsChanged();

    return true;
  }

  public boolean setReadOnly(String name, boolean value) {
    ListData list = getDataByName(name);
    if (list == null || list.isReadOnly) return false;

    list.isReadOnly = value;

    fireChangeListsChanged();

    return true;
  }

  public boolean editName(@NotNull String fromName, @NotNull String toName) {
    if (fromName.equals(toName)) return false;
    if (getDataByName(toName) != null) return false;

    final ListData list = getDataByName(fromName);
    if (list == null || list.isReadOnly) return false;

    list.name = toName;

    fireChangeListsChanged();

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

    fireChangeListsChanged();

    return oldComment;
  }


  @NotNull
  public LocalChangeList addChangeList(@NotNull String name, @Nullable String description, @Nullable String id,
                                       @Nullable ChangeListData data) {
    LocalChangeList existingList = getChangeListByName(name);
    if (existingList != null) {
      LOG.error("Attempt to create duplicate changelist " + name);
      return existingList;
    }

    ListData list = new ListData(id, name);
    list.comment = StringUtil.notNullize(description);
    list.data = data;

    list = putNewListData(list);

    fireChangeListsChanged();

    return toChangeList(list);
  }

  public boolean removeChangeList(@NotNull String name) {
    ListData removedList = getDataByName(name);
    if (removedList == null) return false;

    if (removedList.isDefault) {
      LOG.error("Cannot remove default changelist");
      return false;
    }

    myChangeMappings.replaceAll((change, list) -> {
      if (list == removedList) return myDefault;
      return list;
    });

    myLists.remove(removedList);

    fireChangeListRemoved(removedList.id);
    fireChangeListsChanged();
    myReadOnlyChangesCache = null;

    return true;
  }

  @Nullable
  public MultiMap<LocalChangeList, Change> moveChangesTo(@NotNull String name, @NotNull Change[] changes) {
    final ListData targetList = getDataByName(name);
    if (targetList == null) return null;

    final MultiMap<ListData, Change> result = new MultiMap<>();

    for (Change change : changes) {
      ListData list = myChangeMappings.get(change);
      if (list != null) {
        if (list != targetList) {
          myChangeMappings.replace(change, targetList);

          result.putValue(list, change);
        }
      }
      else {
        PartialChangeTracker tracker = getChangeTrackerFor(change);
        if (tracker != null) {
          if (change instanceof ChangeListChange) {
            String fromListId = ((ChangeListChange)change).getChangeListId();
            ListData fromList = getDataById(fromListId);

            if (fromList != null && fromList != targetList) {
              if (myMainWorker) {
                tracker.moveChanges(fromList.id, targetList.id);
              }

              result.putValue(fromList, change);
            }
          }
          else {
            HashSet<ListData> fromLists = getAffectedLists(tracker);
            fromLists.remove(targetList);

            if (!fromLists.isEmpty()) {
              if (myMainWorker) {
                tracker.moveChangesTo(targetList.id);
              }

              for (ListData fromList : fromLists) {
                result.putValue(fromList, change);
              }
            }
          }
        }
      }
    }

    fireChangeListsChanged();
    myReadOnlyChangesCache = null;

    MultiMap<LocalChangeList, Change> notifications = new MultiMap<>();
    for (Map.Entry<ListData, Collection<Change>> entry : result.entrySet()) {
      notifications.put(toChangeList(entry.getKey()), entry.getValue());
    }
    return notifications;
  }

  /**
   * Called without external lock
   */
  public void notifyChangelistsChanged() {
    if (myReadOnlyChangesCacheInvalidated.compareAndSet(false, true)) {
      fireChangeListsChanged();
    }
  }


  public void applyChangesFromUpdate(@NotNull ChangeListWorker updatedWorker,
                                     @NotNull PlusMinusModify<BaseRevision> deltaListener) {
    boolean somethingChanged = notifyPathsChanged(myIdx, updatedWorker.myIdx, deltaListener);

    myIdx.copyFrom(updatedWorker.myIdx);
    myChangeMappings.clear();

    Map<ListData, ListData> listMapping = copyListsDataFrom(updatedWorker.myLists);

    for (Change change : myIdx.getChanges()) {
      PartialChangeTracker tracker = getChangeTrackerFor(change);
      if (tracker == null) {
        ListData oldList = updatedWorker.myChangeMappings.get(change);
        ListData newList = notNullList(listMapping.get(oldList));
        myChangeMappings.put(change, newList);
      }
    }

    if (somethingChanged) {
      FileStatusManager.getInstance(myProject).fileStatusesChanged();
    }

    fireChangeListsChanged();
  }

  private static boolean notifyPathsChanged(@NotNull ChangeListsIndexes was, @NotNull ChangeListsIndexes became,
                                            @NotNull PlusMinusModify<BaseRevision> deltaListener) {
    final Set<BaseRevision> toRemove = new HashSet<>();
    final Set<BaseRevision> toAdd = new HashSet<>();
    final Set<BeforeAfter<BaseRevision>> toModify = new HashSet<>();
    was.getDelta(became, toRemove, toAdd, toModify);

    for (BaseRevision pair : toRemove) {
      deltaListener.minus(pair);
    }
    for (BaseRevision pair : toAdd) {
      deltaListener.plus(pair);
    }
    for (BeforeAfter<BaseRevision> beforeAfter : toModify) {
      deltaListener.modify(beforeAfter.getBefore(), beforeAfter.getAfter());
    }
    return !toRemove.isEmpty() || !toAdd.isEmpty();
  }

  void setChangeLists(@NotNull Collection<LocalChangeListImpl> lists) {
    myIdx.clear();
    myChangeMappings.clear();

    copyListsDataFrom(ContainerUtil.map(lists, ListData::new));

    for (LocalChangeListImpl list : lists) {
      ListData listData = notNullList(getDataByIdVerify(list.getId()));

      for (Change change : list.getChanges()) {
        if (myIdx.getChanges().contains(change)) continue;

        myIdx.changeAdded(change, null);

        PartialChangeTracker tracker = getChangeTrackerFor(change);
        if (tracker == null) {
          myChangeMappings.put(change, listData);
        }
      }
    }

    fireChangeListsChanged();
  }


  private void fireDefaultListChanged(@NotNull String oldDefaultId, @NotNull String newDefaultId) {
    if (myMainWorker) {
      for (PartialChangeTracker tracker : myPartialChangeTrackers.values()) {
        tracker.defaultListChanged(oldDefaultId, newDefaultId);
      }
    }
  }

  private void fireChangeListRemoved(@NotNull String listId) {
    if (myMainWorker) {
      for (PartialChangeTracker tracker : myPartialChangeTrackers.values()) {
        tracker.changeListRemoved(listId);
      }
    }
  }

  private void fireChangeListsChanged() {
    if (myMainWorker) {
      myDelayedNotificator.changeListsChanged();
    }
  }


  @Nullable
  private ListData removeChangeMapping(@NotNull Change change) {
    ListData oldList = myChangeMappings.remove(change);
    fireChangeListsChanged();
    myReadOnlyChangesCache = null;
    return oldList;
  }

  private void putChangeMapping(@NotNull Change change, @NotNull ListData list) {
    myChangeMappings.put(change, list);
    fireChangeListsChanged();
    myReadOnlyChangesCache = null;
  }

  @NotNull
  private ListData putNewListData(@NotNull ListData list) {
    ListData listWithSameName = getDataByName(list.name);
    if (listWithSameName != null) {
      LOG.error(String.format("Attempt to create changelist with same name: %s - %s", list.name, list.id));
      return listWithSameName;
    }

    ListData listWithSameId = getDataById(list.id);
    if (listWithSameId != null) {
      LOG.error(String.format("Attempt to create changelist with same id: %s - %s", list.name, list.id));
      return listWithSameId;
    }

    myLists.add(list);
    return list;
  }

  @NotNull
  private ListData notNullList(@Nullable ListData listData) {
    if (listData == null) LOG.error("ListData not found");
    return ObjectUtils.notNull(listData, myDefault);
  }

  @Nullable
  private ListData getDataById(@NotNull String id) {
    return ContainerUtil.find(myLists, list -> list.id.equals(id));
  }

  @Nullable
  private ListData getDataByName(@NotNull String name) {
    return ContainerUtil.find(myLists, list -> list.name.equals(name));
  }

  @Nullable
  private ListData getDataByIdVerify(@NotNull String id) {
    ListData list = getDataById(id);
    if (myMainWorker && list == null) LOG.error(String.format("Unknown changelist %s", id));
    return list;
  }

  @Nullable
  private PartialChangeTracker getChangeTrackerFor(@NotNull Change change) {
    if (myPartialChangeTrackers.isEmpty()) return null;

    if (!myIdx.getChanges().contains(change)) return null;
    ContentRevision revision = change.getAfterRevision();
    if (revision == null) return null;
    return myPartialChangeTrackers.get(revision.getFile());
  }

  @NotNull
  private HashSet<ListData> getAffectedLists(@NotNull PartialChangeTracker tracker) {
    HashSet<ListData> data = new HashSet<>();
    for (String listId : tracker.getAffectedChangeListsIds()) {
      ListData partialList = getDataByIdVerify(listId);
      data.add(partialList != null ? partialList : myDefault);
    }
    return data;
  }

  @Contract("!null -> !null; null -> null")
  private LocalChangeListImpl toChangeList(@Nullable ListData data) {
    if (data == null) return null;

    if (myReadOnlyChangesCache == null || myReadOnlyChangesCacheInvalidated.get()) {
      myReadOnlyChangesCacheInvalidated.set(false);
      myReadOnlyChangesCache = getChangesMapping();
    }
    Set<Change> cachedChanges = myReadOnlyChangesCache.get(data);
    Set<Change> changes = cachedChanges != null ? Collections.unmodifiableSet(cachedChanges) : Collections.emptySet();

    return new LocalChangeListImpl.Builder(myProject, data.name)
      .setId(data.id)
      .setComment(data.comment)
      .setChangesCollection(changes)
      .setData(data.data)
      .setDefault(data.isDefault)
      .setReadOnly(data.isReadOnly)
      .build();
  }

  @NotNull
  private static Change toChangeListChange(@NotNull Change change, @NotNull ListData list) {
    if (change.getClass() == Change.class) {
      return new ChangeListChange(change, list.name, list.id);
    }
    return change;
  }

  private static class ListData {
    @NotNull public final String id;

    @NotNull public String name;
    @NotNull public String comment = "";
    @Nullable public ChangeListData data;

    public boolean isDefault = false;
    public boolean isReadOnly = false; // read-only lists cannot be removed or renamed

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
    }

    public ListData(@NotNull ListData list) {
      this.id = list.id;
      this.name = list.name;
      this.comment = list.comment;
      this.data = list.data;
      this.isDefault = list.isDefault;
      this.isReadOnly = list.isReadOnly;
    }
  }

  @Override
  public String toString() {
    return String.format("ChangeListWorker{myMap=%s}", StringUtil.join(myLists, list -> {
      return String.format("list: %s changes: %s", list.name, StringUtil.join(getChangesIn(list), ", "));
    }, "\n"));
  }


  public static class ChangeListUpdater implements ChangeListManagerGate {
    private final ChangeListWorker myWorker;

    private final Map<String, OpenTHashSet<Change>> myChangesBeforeUpdateMap = FactoryMap.create(it -> new OpenTHashSet<>());
    private final Set<String> myListsToDisappear = new HashSet<>();

    public ChangeListUpdater(@NotNull ChangeListWorker worker) {
      myWorker = worker.copy();
    }

    @NotNull
    public Project getProject() {
      return myWorker.getProject();
    }


    public void notifyStartProcessingChanges(@Nullable VcsModifiableDirtyScope scope) {
      myWorker.myChangeMappings.forEach((change, list) -> {
        OpenTHashSet<Change> changes = myChangesBeforeUpdateMap.get(list.id);
        changes.add(change);
      });

      List<Change> removedChanges = removeChangesUnderScope(scope);

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

    @NotNull
    private List<Change> removeChangesUnderScope(@Nullable VcsModifiableDirtyScope scope) {
      List<Change> removed = new ArrayList<>();
      for (Change change : myWorker.myIdx.getChanges()) {
        ContentRevision before = change.getBeforeRevision();
        ContentRevision after = change.getAfterRevision();
        boolean isUnderScope = scope == null ||
                               before != null && scope.belongsTo(before.getFile()) ||
                               after != null && scope.belongsTo(after.getFile()) ||
                               isIgnoredChange(before, after, getProject());
        if (isUnderScope) {
          removed.add(change);
        }
      }

      for (Change change : removed) {
        myWorker.myIdx.changeRemoved(change);
        myWorker.removeChangeMapping(change);
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


    public void notifyDoneProcessingChanges(@NotNull DelayedNotificator dispatcher) {
      List<ChangeList> changedLists = new ArrayList<>();
      final Map<LocalChangeListImpl, List<Change>> removedChanges = new HashMap<>();
      final Map<LocalChangeListImpl, List<Change>> addedChanges = new HashMap<>();
      for (ChangeListWorker.ListData list : myWorker.myLists) {
        final List<Change> removed = new ArrayList<>();
        final List<Change> added = new ArrayList<>();
        boolean wasChanged = doneProcessingChanges(list, removed, added);

        LocalChangeListImpl changeList = myWorker.toChangeList(list);
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
      removedChanges.forEach((changeList, changes) -> {
        dispatcher.changesRemoved(changes, changeList);
      });
      addedChanges.forEach((changeList, changes) -> {
        dispatcher.changesAdded(changes, changeList);
      });
      for (ChangeList changeList : changedLists) {
        dispatcher.changeListChanged(changeList);
      }

      for (String name : myListsToDisappear) {
        final ChangeListWorker.ListData list = myWorker.getDataByName(name);
        if (list != null && myWorker.getChangesIn(list).isEmpty() && !list.isReadOnly && !list.isDefault) {
          myWorker.removeChangeList(name);
        }
      }
      myListsToDisappear.clear();

      myChangesBeforeUpdateMap.clear();
    }

    private boolean doneProcessingChanges(@NotNull ChangeListWorker.ListData list,
                                          @NotNull List<Change> removedChanges,
                                          @NotNull List<Change> addedChanges) {
      OpenTHashSet<Change> changesBeforeUpdate = myChangesBeforeUpdateMap.get(list.id);

      Set<Change> listChanges = new HashSet<>();
      myWorker.myChangeMappings.forEach((change, mappedList) -> {
        if (mappedList == list) listChanges.add(change);
      });

      for (Change newChange : listChanges) {
        Change oldChange = findOldChange(changesBeforeUpdate, newChange);
        if (oldChange == null) {
          addedChanges.add(newChange);
        }
      }

      removedChanges.addAll(changesBeforeUpdate);
      removedChanges.removeAll(listChanges);

      return listChanges.size() != changesBeforeUpdate.size() ||
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
    public ChangeListWorker finish() {
      checkForMultipleCopiesNotMove();
      return myWorker;
    }

    private void checkForMultipleCopiesNotMove() {
      final MultiMap<FilePath, Change> moves = new MultiMap<>();

      for (Change change : myWorker.myIdx.getChanges()) {
        if (change.isMoved() || change.isRenamed()) {
          moves.putValue(change.getBeforeRevision().getFile(), change);
        }
      }
      for (FilePath filePath : moves.keySet()) {
        final List<Change> copies = (List<Change>)moves.get(filePath);
        if (copies.size() == 1) continue;
        copies.sort(CHANGES_AFTER_REVISION_COMPARATOR);
        for (int i = 0; i < (copies.size() - 1); i++) {
          final Change oldChange = copies.get(i);
          final Change newChange = new Change(null, oldChange.getAfterRevision());

          final VcsKey key = myWorker.myIdx.getVcsFor(oldChange);

          myWorker.myIdx.changeRemoved(oldChange);
          myWorker.myIdx.changeAdded(newChange, key);

          ListData list = myWorker.removeChangeMapping(oldChange);
          myWorker.putChangeMapping(newChange, myWorker.notNullList(list));
        }
      }
    }

    private final Comparator<Change> CHANGES_AFTER_REVISION_COMPARATOR = (o1, o2) -> {
      String s1 = o1.getAfterRevision().getFile().getPresentableUrl();
      String s2 = o2.getAfterRevision().getFile().getPresentableUrl();
      return SystemInfo.isFileSystemCaseSensitive ? s1.compareTo(s2) : s1.compareToIgnoreCase(s2);
    };


    public void addChangeToList(@NotNull String name, @NotNull Change change, VcsKey vcsKey) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("[addChangeToList] name: %s change: %s vcs: %s", name, ChangesUtil.getFilePath(change).getPath(),
                                vcsKey == null ? null : vcsKey.getName()));
      }

      ListData list = myWorker.getDataByName(name);
      if (list == null) return;

      addChangeToList(list, change, vcsKey);
    }

    public void addChangeToCorrespondingList(@NotNull Change change, VcsKey vcsKey) {
      if (LOG.isDebugEnabled()) {
        final String path = ChangesUtil.getFilePath(change).getPath();
        LOG.debug("[addChangeToCorrespondingList] for change " + path + " type: " + change.getType() +
                  " have before revision: " + (change.getBeforeRevision() != null));
      }

      for (ChangeListWorker.ListData list : myWorker.myLists) {
        Set<Change> changesBeforeUpdate = myChangesBeforeUpdateMap.get(list.id);
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
      addChangeToList(myWorker.myDefault, change, vcsKey);
    }

    private void addChangeToList(@NotNull ListData list, @NotNull Change change, VcsKey vcsKey) {
      if (myWorker.myIdx.getChanges().contains(change)) {
        LOG.warn(String.format("Multiple equal changes added: %s", change));
        return;
      }

      myWorker.myIdx.changeAdded(change, vcsKey);
      myWorker.putChangeMapping(change, list);
    }

    public void removeRegisteredChangeFor(@Nullable FilePath filePath) {
      Change change = myWorker.getChangeForPath(filePath);
      if (change == null) return;

      myWorker.myIdx.changeRemoved(change);
      myWorker.removeChangeMapping(change);
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
      return myWorker.addChangeList(name, comment, null, null);
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
      myListsToDisappear.addAll(names);
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
      myWorker.setDefaultList(list);
    }
  }


  public interface PartialChangeTracker {
    @NotNull
    List<String> getAffectedChangeListsIds();

    void initChangeTracking(@NotNull String defaultId, @NotNull List<String> changelistsIds);

    void defaultListChanged(@NotNull String oldListId, @NotNull String newListId);

    void changeListRemoved(@NotNull String listId);

    void moveChanges(@NotNull String fromListId, @NotNull String toListId);

    void moveChangesTo(@NotNull String toListId);
  }
}
