/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.DiffProviderEx;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;

import static com.intellij.openapi.vcs.changes.committed.IncomingChangeState.State.*;

/**
 * @author yole
 */
public class ChangesCacheFile {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.committed.ChangesCacheFile");
  private static final int VERSION = 7;

  private final File myPath;
  private final File myIndexPath;
  private RandomAccessFile myStream;
  private RandomAccessFile myIndexStream;
  private boolean myStreamsOpen;
  private final Project myProject;
  private final AbstractVcs myVcs;
  private final CachingCommittedChangesProvider myChangesProvider;
  private final ProjectLevelVcsManager myVcsManager;
  private final FilePath myRootPath;
  private final RepositoryLocation myLocation;
  private Date myFirstCachedDate;
  private Date myLastCachedDate;
  private long myFirstCachedChangelist;
  private long myLastCachedChangelist;
  private int myIncomingCount;
  private boolean myHaveCompleteHistory;
  private boolean myHeaderLoaded;
  @NonNls private static final String INDEX_EXTENSION = ".index";
  private static final int INDEX_ENTRY_SIZE = 3*8+2;
  private static final int HEADER_SIZE = 46;

  public ChangesCacheFile(Project project, File path, AbstractVcs vcs, VirtualFile root, RepositoryLocation location) {
    reset();

    myProject = project;
    myPath = path;
    myIndexPath = new File(myPath.toString() + INDEX_EXTENSION);
    myVcs = vcs;
    myChangesProvider = (CachingCommittedChangesProvider) vcs.getCommittedChangesProvider();
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myRootPath = VcsUtil.getFilePath(root);
    myLocation = location;
  }

  private void reset() {
    final Calendar date = Calendar.getInstance();
    date.set(2020, Calendar.FEBRUARY, 2);
    myFirstCachedDate = date.getTime();
    date.set(1970, Calendar.FEBRUARY, 2);
    myLastCachedDate = date.getTime();
    myIncomingCount = 0;
    myLastCachedChangelist = -1;
    myFirstCachedChangelist = Long.MAX_VALUE;
    myHaveCompleteHistory = false;
    myHeaderLoaded = false;
  }

  public RepositoryLocation getLocation() {
    return myLocation;
  }

  public CachingCommittedChangesProvider getProvider() {
    return myChangesProvider;
  }

  public boolean isEmpty() throws IOException {
    if (!myPath.exists()) {
      return true;
    }
    try {
      loadHeader();
    }
    catch(VersionMismatchException ex) {
      myPath.delete();
      myIndexPath.delete();
      return true;
    }
    catch(EOFException ex) {
      myPath.delete();
      myIndexPath.delete();
      return true;
    }

    return false;
  }

  public void delete() {
    FileUtil.delete(myPath);
    FileUtil.delete(myIndexPath);
    try {
      closeStreams();
    }
    catch (IOException e) {
      //
    }
  }

  public List<CommittedChangeList> writeChanges(final List<CommittedChangeList> changes) throws IOException {
    // the list and index are sorted in direct chronological order
    Collections.sort(changes, CommittedChangeListByDateComparator.ASCENDING);
    return writeChanges(changes, null);
  }

  public List<CommittedChangeList> writeChanges(final List<CommittedChangeList> changes, @Nullable final List<Boolean> present) throws IOException {
    assert present == null || present.size() == changes.size();

    List<CommittedChangeList> result = new ArrayList<>(changes.size());
    boolean wasEmpty = isEmpty();
    openStreams();
    try {
      if (wasEmpty) {
        myHeaderLoaded = true;
        writeHeader();
      }
      myStream.seek(myStream.length());
      IndexEntry[] entries = readLastIndexEntries(0, changes.size());

      final Iterator<Boolean> iterator = present == null ? null : present.iterator();
      for(CommittedChangeList list: changes) {
        boolean duplicate = false;
        for(IndexEntry entry: entries) {
          if (list.getCommitDate().getTime() == entry.date && list.getNumber() == entry.number) {
            duplicate = true;
            break;
          }
        }
        if (duplicate) {
          debug("Skipping duplicate changelist " + list.getNumber());
          continue;
        }
        debug("Writing incoming changelist " + list.getNumber());
        result.add(list);
        long position = myStream.getFilePointer();
        //noinspection unchecked
        myChangesProvider.writeChangeList(myStream, list);
        updateCachedRange(list);
        writeIndexEntry(list.getNumber(), list.getCommitDate().getTime(), position, present == null ? false : iterator.next());
        myIncomingCount++;
      }
      writeHeader();
      myHeaderLoaded = true;
    }
    finally {
      closeStreams();
    }
    return result;
  }

  private static void debug(@NonNls String message) {
    LOG.debug(message);
  }

  private void updateCachedRange(final CommittedChangeList list) {
    if (list.getCommitDate().getTime() > myLastCachedDate.getTime()) {
      myLastCachedDate = list.getCommitDate();
    }
    if (list.getCommitDate().getTime() < myFirstCachedDate.getTime()) {
      myFirstCachedDate = list.getCommitDate();
    }
    if (list.getNumber() < myFirstCachedChangelist) {
      myFirstCachedChangelist = list.getNumber();
    }
    if (list.getNumber() > myLastCachedChangelist) {
      myLastCachedChangelist = list.getNumber();
    }
  }

  private void writeIndexEntry(long number, long date, long offset, boolean completelyDownloaded) throws IOException {
    myIndexStream.writeLong(number);
    myIndexStream.writeLong(date);
    myIndexStream.writeLong(offset);
    myIndexStream.writeShort(completelyDownloaded ? 1 : 0);
  }

  private void openStreams() throws FileNotFoundException {
    myStream = new RandomAccessFile(myPath, "rw");
    myIndexStream = new RandomAccessFile(myIndexPath, "rw");
    myStreamsOpen = true;
  }

  private void closeStreams() throws IOException {
    myStreamsOpen = false;
    try {
      if (myStream != null) {
        myStream.close();
      }
    }
    finally {
      if (myIndexStream != null) {
        myIndexStream.close();
      }
    }
  }

  private void writeHeader() throws IOException {
    assert myStreamsOpen && myHeaderLoaded;
    myStream.seek(0);
    myStream.writeInt(VERSION);
    myStream.writeInt(myChangesProvider.getFormatVersion());
    myStream.writeLong(myLastCachedDate.getTime());
    myStream.writeLong(myFirstCachedDate.getTime());
    myStream.writeLong(myFirstCachedChangelist);
    myStream.writeLong(myLastCachedChangelist);
    myStream.writeShort(myHaveCompleteHistory ? 1 : 0);
    myStream.writeInt(myIncomingCount);
    debug("Saved header for cache of " + myLocation + ": last cached date=" + myLastCachedDate +
             ", last cached number=" + myLastCachedChangelist + ", incoming count=" + myIncomingCount);
  }

  private IndexEntry[] readIndexEntriesByOffset(final long offsetFromStart, int count) throws IOException {
    if (!myIndexPath.exists()) {
      return NO_ENTRIES;
    }
    long totalCount = myIndexStream.length() / INDEX_ENTRY_SIZE;
    if (count > (totalCount - offsetFromStart)) {
      count = (int) (totalCount - offsetFromStart);
    }
    if (count == 0) {
      return NO_ENTRIES;
    }
    // offset from start
    myIndexStream.seek(INDEX_ENTRY_SIZE * offsetFromStart);
    IndexEntry[] result = new IndexEntry[count];
    for(int i = (count - 1); i >= 0; --i) {
      result [i] = new IndexEntry();
      readIndexEntry(result [i]);
    }
    return result;
  }

  private IndexEntry[] readLastIndexEntries(int offset, int count) throws IOException {
    if (!myIndexPath.exists()) {
      return NO_ENTRIES;
    }
    long totalCount = myIndexStream.length() / INDEX_ENTRY_SIZE;
    if (count > totalCount - offset) {
      count = (int)totalCount - offset;
    }
    if (count == 0) {
      return NO_ENTRIES;
    }
    myIndexStream.seek(myIndexStream.length() - INDEX_ENTRY_SIZE * (count + offset));
    IndexEntry[] result = new IndexEntry[count];
    for(int i=0; i<count; i++) {
      result [i] = new IndexEntry();
      readIndexEntry(result [i]);
    }
    return result;
  }

  private void readIndexEntry(final IndexEntry result) throws IOException {
    result.number = myIndexStream.readLong();
    result.date = myIndexStream.readLong();
    result.offset = myIndexStream.readLong();
    result.completelyDownloaded = (myIndexStream.readShort() != 0);
  }

  public Date getLastCachedDate() throws IOException {
    loadHeader();
    return myLastCachedDate;
  }

  public Date getFirstCachedDate() throws IOException {
    loadHeader();
    return myFirstCachedDate;
  }

  public long getFirstCachedChangelist() throws IOException {
    loadHeader();
    return myFirstCachedChangelist;
  }

  public long getLastCachedChangelist() throws IOException {
    loadHeader();
    return myLastCachedChangelist;
  }

  private void loadHeader() throws IOException {
    if (!myHeaderLoaded) {
      RandomAccessFile stream = new RandomAccessFile(myPath, "r");
      try {
        int version = stream.readInt();
        if (version != VERSION) {
          throw new VersionMismatchException();
        }
        int providerVersion = stream.readInt();
        if (providerVersion != myChangesProvider.getFormatVersion()) {
          throw new VersionMismatchException();
        }
        myLastCachedDate = new Date(stream.readLong());
        myFirstCachedDate = new Date(stream.readLong());
        myFirstCachedChangelist = stream.readLong();
        myLastCachedChangelist = stream.readLong();
        myHaveCompleteHistory = (stream.readShort() != 0);
        myIncomingCount = stream.readInt();
        assert stream.getFilePointer() == HEADER_SIZE;
      }
      finally {
        stream.close();
      }
      myHeaderLoaded = true;
    }
  }

  public Iterator<ChangesBunch> getBackBunchedIterator(final int bunchSize) {
    return new BackIterator(bunchSize);
  }

  private List<Boolean> loadAllData(final List<CommittedChangeList> lists) throws IOException {
    List<Boolean> idx = new ArrayList<>();
    openStreams();

    try {
      loadHeader();
      final long length = myIndexStream.length();
      long totalCount = length / INDEX_ENTRY_SIZE;
      for(int i=0; i<totalCount; i++) {
        final long indexOffset = length - (i + 1) * INDEX_ENTRY_SIZE;
        myIndexStream.seek(indexOffset);
        IndexEntry e = new IndexEntry();
        readIndexEntry(e);
        final CommittedChangeList list = loadChangeListAt(e.offset);
        lists.add(list);
        idx.add(e.completelyDownloaded);
      }
    } finally {
      closeStreams();
    }
    return idx;
  }

  public void editChangelist(long number, String message) throws IOException {
    final List<CommittedChangeList> lists = new ArrayList<>();
    final List<Boolean> present = loadAllData(lists);
    for (CommittedChangeList list : lists) {
      if (list.getNumber() == number) {
        list.setDescription(message);
        break;
      }
    }
    delete();
    Collections.reverse(lists);
    Collections.reverse(present);
    writeChanges(lists, present);
  }

  private class BackIterator implements Iterator<ChangesBunch> {
    private final int bunchSize;
    private long myOffset;

    private BackIterator(final int bunchSize) {
      this.bunchSize = bunchSize;
      try {
        try {
          openStreams();
          myOffset = (myIndexStream.length() / INDEX_ENTRY_SIZE);
        } finally {
          closeStreams();
        }
      }
      catch (IOException e) {
        myOffset = -1;
      }
    }

    public boolean hasNext() {
      return myOffset > 0;
    }

    @Nullable
    public ChangesBunch next() {
      try {
        final int size;
        if (myOffset < bunchSize) {
          size = (int) myOffset;
          myOffset = 0;
        } else {
          myOffset -= bunchSize;
          size = bunchSize;
        }
        return new ChangesBunch(readChangesInterval(myOffset, size), true);
      }
      catch (IOException e) {
        LOG.error(e);
        return null;
      }
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private List<CommittedChangeList> readChangesInterval(final long indexOffset, final int number) throws IOException {
    openStreams();

    try {
      IndexEntry[] entries = readIndexEntriesByOffset(indexOffset, number);
      if (entries.length == 0) {
        return Collections.emptyList();
      }

      final List<CommittedChangeList> result = new ArrayList<>();
      for (IndexEntry entry : entries) {
        final CommittedChangeList changeList = loadChangeListAt(entry.offset);
        result.add(changeList);
      }
      return result;
    } finally {
      closeStreams();
    }
  }

  public List<CommittedChangeList> readChanges(final ChangeBrowserSettings settings, final int maxCount) throws IOException {
    final List<CommittedChangeList> result = new ArrayList<>();
    final ChangeBrowserSettings.Filter filter = settings.createFilter();
    openStreams();
    try {
      if (maxCount == 0) {
        myStream.seek(HEADER_SIZE);  // skip header
        while(myStream.getFilePointer() < myStream.length()) {
          CommittedChangeList changeList = myChangesProvider.readChangeList(myLocation, myStream);
          if (filter.accepts(changeList)) {
            result.add(changeList);
          }
        }
      }
      else if (!settings.isAnyFilterSpecified()) {
        IndexEntry[] entries = readLastIndexEntries(0, maxCount);
        for(IndexEntry entry: entries) {
          myStream.seek(entry.offset);
          result.add(myChangesProvider.readChangeList(myLocation, myStream));
        }
      }
      else {
        int offset = 0;
        while(result.size() < maxCount) {
          IndexEntry[] entries = readLastIndexEntries(offset, 1);
          if (entries.length == 0) {
            break;
          }
          CommittedChangeList changeList = loadChangeListAt(entries [0].offset);
          if (filter.accepts(changeList)) {
            result.add(0, changeList);
          }
          offset++;
        }
      }
      return result;
    }
    finally {
      closeStreams();
    }
  }

  public boolean hasCompleteHistory() {
    return myHaveCompleteHistory;
  }

  public void setHaveCompleteHistory(final boolean haveCompleteHistory) {
    if (myHaveCompleteHistory != haveCompleteHistory) {
      myHaveCompleteHistory = haveCompleteHistory;
      try {
        openStreams();
        try {
          writeHeader();
        }
        finally {
          closeStreams();
        }
      }
      catch(IOException ex) {
        LOG.error(ex);
      }
    }
  }

  public List<CommittedChangeList> loadIncomingChanges() throws IOException {
    List<CommittedChangeList> result = new ArrayList<>();
    int offset = 0;
    openStreams();
    try {
      while(true) {
        IndexEntry[] entries = readLastIndexEntries(offset, 1);
        if (entries.length == 0) {
          break;
        }
        if (!entries [0].completelyDownloaded) {
          IncomingChangeListData data = readIncomingChangeListData(offset, entries [0]);
          if (data.accountedChanges.size() == 0) {
            result.add(data.changeList);
          }
          else {
            ReceivedChangeList changeList = new ReceivedChangeList(data.changeList);
            for(Change change: data.changeList.getChanges()) {
              if (!data.accountedChanges.contains(change)) {
                changeList.addChange(change);
              }
            }
            result.add(changeList);
          }
          if (result.size() == myIncomingCount) break;
        }
        offset++;
      }
      debug("Loaded " + result.size() + " incoming changelists");
    }
    finally {
      closeStreams();
    }
    return result;
  }

  private CommittedChangeList loadChangeListAt(final long clOffset) throws IOException {
    myStream.seek(clOffset);
    return myChangesProvider.readChangeList(myLocation, myStream);
  }

  public boolean processUpdatedFiles(UpdatedFiles updatedFiles, Collection<CommittedChangeList> receivedChanges) throws IOException {
    boolean haveUnaccountedUpdatedFiles = false;
    openStreams();
    loadHeader();
    ReceivedChangeListTracker tracker = new ReceivedChangeListTracker();
    try {
      final List<IncomingChangeListData> incomingData = loadIncomingChangeListData();
      for(FileGroup group: updatedFiles.getTopLevelGroups()) {
        haveUnaccountedUpdatedFiles |= processGroup(group, incomingData, tracker);
      }
      if (!haveUnaccountedUpdatedFiles) {
        for(IncomingChangeListData data: incomingData) {
          saveIncoming(data, false);
        }
        writeHeader();
      }
    }
    finally {
      closeStreams();
    }
    receivedChanges.addAll(tracker.getChangeLists());
    return haveUnaccountedUpdatedFiles;
  }

  private void saveIncoming(final IncomingChangeListData data, boolean haveNoMoreIncoming) throws IOException {
    writePartial(data, haveNoMoreIncoming);
    if (data.accountedChanges.size() == data.changeList.getChanges().size() || haveNoMoreIncoming) {
      debug("Removing changelist " + data.changeList.getNumber() + " from incoming changelists");
      myIndexStream.seek(data.indexOffset);
      writeIndexEntry(data.indexEntry.number, data.indexEntry.date, data.indexEntry.offset, true);
      myIncomingCount--;
    }
  }

  private boolean processGroup(final FileGroup group, final List<IncomingChangeListData> incomingData,
                               final ReceivedChangeListTracker tracker) {
    boolean haveUnaccountedUpdatedFiles = false;
    final List<Pair<String,VcsRevisionNumber>> list = group.getFilesAndRevisions(myVcsManager);
    for(Pair<String, VcsRevisionNumber> pair: list) {
      final String file = pair.first;
      FilePath path = VcsUtil.getFilePath(file, false);
      if (!path.isUnder(myRootPath, false) || pair.second == null) {
        continue;
      }
      if (group.getId().equals(FileGroup.REMOVED_FROM_REPOSITORY_ID)) {
        haveUnaccountedUpdatedFiles |= processDeletedFile(path, incomingData, tracker);
      }
      else {
        haveUnaccountedUpdatedFiles |= processFile(path, pair.second, incomingData, tracker);
      }
    }
    for(FileGroup childGroup: group.getChildren()) {
      haveUnaccountedUpdatedFiles |= processGroup(childGroup, incomingData, tracker);
    }
    return haveUnaccountedUpdatedFiles;
  }

  private static boolean processFile(final FilePath path,
                                     final VcsRevisionNumber number,
                                     final List<IncomingChangeListData> incomingData,
                                     final ReceivedChangeListTracker tracker) {
    boolean foundRevision = false;
    debug("Processing updated file " + path + ", revision " + number);
    for(IncomingChangeListData data: incomingData) {
      for(Change change: data.changeList.getChanges()) {
        ContentRevision afterRevision = change.getAfterRevision();
        if (afterRevision != null && afterRevision.getFile().equals(path)) {
          int rc = number.compareTo(afterRevision.getRevisionNumber());
          if (rc == 0) {
            foundRevision = true;
          }
          if (rc >= 0) {
            tracker.addChange(data.changeList, change);
            data.accountedChanges.add(change);
          }
        }
      }
    }
    debug(foundRevision ? "All changes for file found" : "Some of changes for file not found");
    return !foundRevision;
  }

  private static boolean processDeletedFile(final FilePath path,
                                            final List<IncomingChangeListData> incomingData,
                                            final ReceivedChangeListTracker tracker) {
    boolean foundRevision = false;
    for(IncomingChangeListData data: incomingData) {
      for(Change change: data.changeList.getChanges()) {
        ContentRevision beforeRevision = change.getBeforeRevision();
        if (beforeRevision != null && beforeRevision.getFile().equals(path)) {
          tracker.addChange(data.changeList, change);
          data.accountedChanges.add(change);
          if (change.getAfterRevision() == null) {
            foundRevision = true;
          }
        }
      }
    }
    return !foundRevision;
  }

  private List<IncomingChangeListData> loadIncomingChangeListData() throws IOException {
    final long length = myIndexStream.length();
    long totalCount = length / INDEX_ENTRY_SIZE;
    List<IncomingChangeListData> incomingData = new ArrayList<>();
    for(int i=0; i<totalCount; i++) {
      final long indexOffset = length - (i + 1) * INDEX_ENTRY_SIZE;
      myIndexStream.seek(indexOffset);
      IndexEntry e = new IndexEntry();
      readIndexEntry(e);
      if (!e.completelyDownloaded) {
        incomingData.add(readIncomingChangeListData(indexOffset, e));
        if (incomingData.size() == myIncomingCount) {
          break;
        }
      }
    }
    debug("Loaded " + incomingData.size() + " incoming changelist pointers");
    return incomingData;
  }

  private IncomingChangeListData readIncomingChangeListData(final long indexOffset, final IndexEntry e) throws IOException {
    IncomingChangeListData data = new IncomingChangeListData();
    data.indexOffset = indexOffset;
    data.indexEntry = e;
    data.changeList = loadChangeListAt(e.offset);
    readPartial(data);
    return data;
  }

  private void writePartial(final IncomingChangeListData data, boolean haveNoMoreIncoming) throws IOException {
    File partialFile = getPartialPath(data.indexEntry.offset);
    final int accounted = data.accountedChanges.size();
    if (haveNoMoreIncoming || accounted == data.changeList.getChanges().size()) {
      partialFile.delete();
    }
    else if (accounted > 0) {
      RandomAccessFile file = new RandomAccessFile(partialFile, "rw");
      try {
        file.writeInt(accounted);
        for(Change c: data.accountedChanges) {
          boolean isAfterRevision = true;
          ContentRevision revision = c.getAfterRevision();
          if (revision == null) {
            isAfterRevision = false;
            revision = c.getBeforeRevision();
            assert revision != null;
          }
          file.writeByte(isAfterRevision ? 1 : 0);
          file.writeUTF(revision.getFile().getIOFile().toString());
        }
      }
      finally {
        file.close();
      }
    }
  }

  private void readPartial(IncomingChangeListData data) {
    HashSet<Change> result = new HashSet<>();
    try {
      File partialFile = getPartialPath(data.indexEntry.offset);
      if (partialFile.exists()) {
        RandomAccessFile file = new RandomAccessFile(partialFile, "r");
        try {
          int count = file.readInt();
          if (count > 0) {
            final Collection<Change> changes = data.changeList.getChanges();
            final Map<String, Change> beforePaths = new HashMap<>();
            final Map<String, Change> afterPaths = new HashMap<>();
            for (Change change : changes) {
              if (change.getBeforeRevision() != null) {
                beforePaths.put(FilePathsHelper.convertPath(change.getBeforeRevision().getFile()), change);
              }
              if (change.getAfterRevision() != null) {
                afterPaths.put(FilePathsHelper.convertPath(change.getAfterRevision().getFile()), change);
              }
            }
            for(int i=0; i<count; i++) {
              boolean isAfterRevision = (file.readByte() != 0);
              String path = file.readUTF();
              final String converted = FilePathsHelper.convertPath(path);
              final Change change;
              if (isAfterRevision) {
                change = afterPaths.get(converted);
              } else {
                change = beforePaths.get(converted);
              }
              if (change != null) {
                result.add(change);
              }
            }
          }
        }
        finally {
          file.close();
        }
      }
    }
    catch(IOException ex) {
      LOG.error(ex);
    }
    data.accountedChanges = result;
  }

  @NonNls
  private File getPartialPath(final long offset) {
    return new File(myPath + "." + offset + ".partial");
  }

  public boolean refreshIncomingChanges() throws IOException, VcsException {
    if (myProject.isDisposed()) return false;
    
    DiffProvider diffProvider = myVcs.getDiffProvider();
    if (diffProvider == null) return false;
    
    return new RefreshIncomingChangesOperation(this, myProject, diffProvider).invoke();
  }

  public AbstractVcs getVcs() {
    return myVcs;
  }

  public FilePath getRootPath() {
    return myRootPath;
  }

  private static class RefreshIncomingChangesOperation {
    private final Set<FilePath> myDeletedFiles = new HashSet<>();
    private final Set<FilePath> myCreatedFiles = new HashSet<>();
    private final Set<FilePath> myReplacedFiles = new HashSet<>();
    private final Map<Long, IndexEntry> myIndexEntryCache = new HashMap<>();
    private final Map<Long, CommittedChangeList> myPreviousChangeListsCache = new HashMap<>();
    private final ChangeListManagerImpl myClManager;
    private final ChangesCacheFile myChangesCacheFile;
    private final Project myProject;
    private final DiffProvider myDiffProvider;
    private boolean myAnyChanges;
    private long myIndexStreamCachedLength;

    RefreshIncomingChangesOperation(ChangesCacheFile changesCacheFile, Project project, final DiffProvider diffProvider) {
      myChangesCacheFile = changesCacheFile;
      myProject = project;
      myDiffProvider = diffProvider;
      myClManager = ChangeListManagerImpl.getInstanceImpl(project);
    }

    public boolean invoke() throws VcsException, IOException {
      myChangesCacheFile.myLocation.onBeforeBatch();
      final Collection<FilePath> incomingFiles = myChangesCacheFile.myChangesProvider.getIncomingFiles(myChangesCacheFile.myLocation);

      myAnyChanges = false;
      myChangesCacheFile.openStreams();
      myChangesCacheFile.loadHeader();
      try {
        IncomingChangeState.header(myChangesCacheFile.myLocation.toPresentableString());

        final List<IncomingChangeListData> list = myChangesCacheFile.loadIncomingChangeListData();
        boolean shouldChangeHeader;
        if (incomingFiles != null && incomingFiles.isEmpty()) {
          // we should just delete any partial files
          shouldChangeHeader = ! list.isEmpty();
          for (IncomingChangeListData data : list) {
            myChangesCacheFile.saveIncoming(data, true);
          }
        } else {
          shouldChangeHeader = refreshIncomingInFile(incomingFiles, list);
        }

        IncomingChangeState.footer();
        if (shouldChangeHeader) {
          myChangesCacheFile.writeHeader();
        }
      }
      finally {
        myChangesCacheFile.myLocation.onAfterBatch();
        myChangesCacheFile.closeStreams();
      }
      return myAnyChanges;
    }

    private boolean refreshIncomingInFile(Collection<FilePath> incomingFiles, List<IncomingChangeListData> list) throws IOException {
      // the incoming changelist pointers are actually sorted in reverse chronological order,
      // so we process file delete changes before changes made to deleted files before they were deleted
      
      Map<Pair<IncomingChangeListData, Change>, VirtualFile> revisionDependentFiles = ContainerUtil.newHashMap();
      Map<Pair<IncomingChangeListData, Change>, ProcessingResult> results = ContainerUtil.newHashMap();

      myIndexStreamCachedLength = myChangesCacheFile.myIndexStream.length();
      // try to process changelists in a light way, remember which files need revisions
      for(IncomingChangeListData data: list) {
        debug("Checking incoming changelist " + data.changeList.getNumber());

        for(Change change: data.getChangesToProcess()) {
          final ProcessingResult result = processIncomingChange(change, data, incomingFiles);
          
          Pair<IncomingChangeListData, Change> key = Pair.create(data, change);
          results.put(key, result);
          if (result.revisionDependentProcessing != null) {
            revisionDependentFiles.put(key, result.file);
          }
        }
      }

      if (!revisionDependentFiles.isEmpty()) {
        // lots of same files could be collected - make set of unique files
        HashSet<VirtualFile> uniqueFiles = ContainerUtil.newHashSet(revisionDependentFiles.values());
        // bulk-get all needed revisions at once
        Map<VirtualFile, VcsRevisionNumber> revisions = myDiffProvider instanceof DiffProviderEx
                                                        ? ((DiffProviderEx)myDiffProvider).getCurrentRevisions(uniqueFiles)
                                                        : DiffProviderEx.getCurrentRevisions(uniqueFiles, myDiffProvider);

        // perform processing requiring those revisions
        for(IncomingChangeListData data: list) {
          for (Change change : data.getChangesToProcess()) {
            Pair<IncomingChangeListData, Change> key = Pair.create(data, change);
            Function<VcsRevisionNumber, ProcessingResult> revisionHandler = results.get(key).revisionDependentProcessing;
            if (revisionHandler != null) {
              results.put(key, revisionHandler.fun(revisions.get(revisionDependentFiles.get(key))));
            }
          }
        }
      }

      // collect and save processing results
      for(IncomingChangeListData data: list) {
        boolean updated = false;
        boolean anyChangeFound = false;
        for (Change change : data.getChangesToProcess()) {
          final ContentRevision revision = (change.getAfterRevision() == null) ? change.getBeforeRevision() : change.getAfterRevision();
          assert revision != null;
          ProcessingResult result = results.get(Pair.create(data, change));
          new IncomingChangeState(change, revision.getRevisionNumber().asString(), result.state).logSelf();
          if (result.changeFound) {
            updated = true;
            data.accountedChanges.add(change);
          } else {
            anyChangeFound = true;
          }
        }
        if (updated || ! anyChangeFound) {
          myAnyChanges = true;
          myChangesCacheFile.saveIncoming(data, !anyChangeFound);
        }
      }
      return myAnyChanges || !list.isEmpty();
    }
    
    private static class ProcessingResult {
      final boolean changeFound; 
      final IncomingChangeState.State state;
      final VirtualFile file;
      final Function<VcsRevisionNumber, ProcessingResult> revisionDependentProcessing;

      private ProcessingResult(boolean changeFound, IncomingChangeState.State state) {
        this.changeFound = changeFound;
        this.state = state;
        this.file = null;
        this.revisionDependentProcessing = null;
      }

      private ProcessingResult(VirtualFile file, Function<VcsRevisionNumber, ProcessingResult> revisionDependentProcessing) {
        this.file = file;
        this.revisionDependentProcessing = revisionDependentProcessing;
        this.changeFound = false;
        this.state = null;
      }
    }

    private ProcessingResult processIncomingChange(final Change change,
                                          final IncomingChangeListData changeListData,
                                          @Nullable final Collection<FilePath> incomingFiles) {
      final CommittedChangeList changeList = changeListData.changeList;
      final ContentRevision afterRevision = change.getAfterRevision();
      if (afterRevision != null) {
        if (afterRevision.getFile().isNonLocal()) {
          // don't bother to search for non-local paths on local disk
          return new ProcessingResult(true, AFTER_DOES_NOT_MATTER_NON_LOCAL);
        }
        if (change.getBeforeRevision() == null) {
          final FilePath path = afterRevision.getFile();
          debug("Marking created file " + path);
          myCreatedFiles.add(path);
        }
        else if (change.getBeforeRevision().getFile().getPath().equals(afterRevision.getFile().getPath()) && change.isIsReplaced()) {
          myReplacedFiles.add(afterRevision.getFile());
        }
        if (incomingFiles != null && !incomingFiles.contains(afterRevision.getFile())) {
          debug("Skipping new/changed file outside of incoming files: " + afterRevision.getFile());
          return new ProcessingResult(true, AFTER_DOES_NOT_MATTER_OUTSIDE_INCOMING);
        }
        debug("Checking file " + afterRevision.getFile().getPath());
        FilePath localPath = ChangesUtil.getLocalPath(myProject, afterRevision.getFile());

        if (! FileUtil.isAncestor(myChangesCacheFile.myRootPath.getIOFile(), localPath.getIOFile(), false)) {
          // alien change in list; skip
          debug("Alien path " +
                localPath.getPresentableUrl() +
                " under root " +
                myChangesCacheFile.myRootPath.getPresentableUrl() +
                "; skipping.");
          return new ProcessingResult(true, AFTER_DOES_NOT_MATTER_ALIEN_PATH);
        }

        final VirtualFile file = localPath.getVirtualFile();
        if (isDeletedFile(myDeletedFiles, afterRevision, myReplacedFiles)) {
          debug("Found deleted file");
          return new ProcessingResult(true, AFTER_DOES_NOT_MATTER_DELETED_FOUND_IN_INCOMING_LIST);
        }
        else if (file != null) {
          return new ProcessingResult(file, new Function<VcsRevisionNumber, ProcessingResult>() {
            @Override
            public ProcessingResult fun(VcsRevisionNumber revision) {
              if (revision != null) {
                debug("Current revision is " + revision + ", changelist revision is " + afterRevision.getRevisionNumber());
                //noinspection unchecked
                if (myChangesCacheFile.myChangesProvider
                  .isChangeLocallyAvailable(afterRevision.getFile(), revision, afterRevision.getRevisionNumber(), changeList)) {
                  return new ProcessingResult(true, AFTER_EXISTS_LOCALLY_AVAILABLE);
                }
                return new ProcessingResult(false, AFTER_EXISTS_NOT_LOCALLY_AVAILABLE);
              }
              debug("Failed to fetch revision");
              return new ProcessingResult(false, AFTER_EXISTS_REVISION_NOT_LOADED);
            }
          });
        }
        else {
          //noinspection unchecked
          if (myChangesCacheFile.myChangesProvider.isChangeLocallyAvailable(afterRevision.getFile(), null, afterRevision.getRevisionNumber(), changeList)) {
            return new ProcessingResult(true, AFTER_NOT_EXISTS_LOCALLY_AVAILABLE);
          }
          if (fileMarkedForDeletion(localPath)) {
            debug("File marked for deletion and not committed jet.");
            return new ProcessingResult(true, AFTER_NOT_EXISTS_MARKED_FOR_DELETION);
          }
          if (wasSubsequentlyDeleted(afterRevision.getFile(), changeListData.indexOffset)) {
            return new ProcessingResult(true, AFTER_NOT_EXISTS_SUBSEQUENTLY_DELETED);
          }
          debug("Could not find local file for change " + afterRevision.getFile().getPath());
          return new ProcessingResult(false, AFTER_NOT_EXISTS_OTHER);
        }
      }
      else {
        final ContentRevision beforeRevision = change.getBeforeRevision();
        assert beforeRevision != null;
        debug("Checking deleted file " + beforeRevision.getFile());
        myDeletedFiles.add(beforeRevision.getFile());
        if (incomingFiles != null && !incomingFiles.contains(beforeRevision.getFile())) {
          debug("Skipping deleted file outside of incoming files: " + beforeRevision.getFile());
          return new ProcessingResult(true, BEFORE_DOES_NOT_MATTER_OUTSIDE);
        }
        if (beforeRevision.getFile().getVirtualFile() == null || myCreatedFiles.contains(beforeRevision.getFile())) {
          // if not deleted from vcs, mark as incoming, otherwise file already deleted
          final boolean locallyDeleted = myClManager.isContainedInLocallyDeleted(beforeRevision.getFile());
          debug(locallyDeleted ? "File deleted locally, change marked as incoming" : "File already deleted");
          return new ProcessingResult(!locallyDeleted, locallyDeleted ? BEFORE_NOT_EXISTS_DELETED_LOCALLY : BEFORE_NOT_EXISTS_ALREADY_DELETED);
        }
        else if (!myChangesCacheFile.myVcs.fileExistsInVcs(beforeRevision.getFile())) {
          debug("File exists locally and is unversioned");
          return new ProcessingResult(true, BEFORE_UNVERSIONED_INSTEAD_OF_VERS_DELETED);
        }
        else {
          final VirtualFile file = beforeRevision.getFile().getVirtualFile();
          return new ProcessingResult(file, new Function<VcsRevisionNumber, ProcessingResult>() {
            @Override
            public ProcessingResult fun(VcsRevisionNumber currentRevision) {
              if ((currentRevision != null) && (currentRevision.compareTo(beforeRevision.getRevisionNumber()) > 0)) {
                // revived in newer revision - possibly was added file with same name
                debug("File with same name was added after file deletion");
                return new ProcessingResult(true, BEFORE_SAME_NAME_ADDED_AFTER_DELETION);
              }
              debug("File exists locally and no 'create' change found for it");
              return new ProcessingResult(false, BEFORE_EXISTS_BUT_SHOULD_NOT);
            }
          });
        }
      }
    }

    private boolean fileMarkedForDeletion(final FilePath localPath) {
      final List<LocalChangeList> changeLists =  myClManager.getChangeListsCopy();
      for (LocalChangeList list : changeLists) {
        final Collection<Change> changes = list.getChanges();
        for (Change change : changes) {
          if (change.getBeforeRevision() != null && change.getBeforeRevision().getFile() != null &&
              change.getBeforeRevision().getFile().getPath().equals(localPath.getPath())) {
            if (FileStatus.DELETED.equals(change.getFileStatus()) || change.isMoved() || change.isRenamed()) {
              return true;
            }
          }
        }
      }
      return false;
    }

    // If we have an incoming add, we may have already processed the subsequent delete of the same file during
    // a previous incoming changes refresh. So we try to search for the deletion of this file through all
    // subsequent committed changelists, regardless of whether they are in "incoming" status.
    private boolean wasSubsequentlyDeleted(final FilePath file, long indexOffset) {
      try {
        indexOffset += INDEX_ENTRY_SIZE;
        while(indexOffset < myIndexStreamCachedLength) {
          IndexEntry e = getIndexEntryAtOffset(indexOffset);

          final CommittedChangeList changeList = getChangeListAtOffset(e.offset);
          for(Change c: changeList.getChanges()) {
            final ContentRevision beforeRevision = c.getBeforeRevision();
            if ((beforeRevision != null) && (c.getAfterRevision() == null)) {
              if (isFileDeleted(file, beforeRevision.getFile())) {
                return true;
              }
            } else if ((beforeRevision != null) && (c.getAfterRevision() != null)) {
              if (isParentReplacedOrFileMoved(file, c, beforeRevision.getFile())) {
                return true;
              }
            }
          }
          indexOffset += INDEX_ENTRY_SIZE;
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
      return false;
    }

    private static boolean isParentReplacedOrFileMoved(@NotNull FilePath file, @NotNull Change change, @NotNull FilePath beforeFile) {
      boolean isParentReplaced = change.isIsReplaced() && (!file.equals(beforeFile));
      boolean isMovedRenamed = change.isMoved() || change.isRenamed();
      // call FilePath.isUnder() only if change is either "parent replaced" or moved/renamed - as many calls to FilePath.isUnder()
      // could take a lot of time
      boolean underBefore = (isParentReplaced || isMovedRenamed) && file.isUnder(beforeFile, false);

      if (underBefore && isParentReplaced) {
        debug("For " + file + "some of parents is replaced: " + beforeFile);
        return true;
      }
      else if (underBefore && isMovedRenamed) {
        debug("For " + file + "some of parents was renamed/moved: " + beforeFile);
        return true;
      }
      return false;
    }

    private static boolean isFileDeleted(@NotNull FilePath file, @NotNull FilePath beforeFile) {
      if (file.getPath().equals(beforeFile.getPath()) || file.isUnder(beforeFile, false)) {
        debug("Found subsequent deletion for file " + file);
        return true;
      }
      return false;
    }

    private IndexEntry getIndexEntryAtOffset(final long indexOffset) throws IOException {
      IndexEntry e = myIndexEntryCache.get(indexOffset);
      if (e == null) {
        myChangesCacheFile.myIndexStream.seek(indexOffset);
        e = new IndexEntry();
        myChangesCacheFile.readIndexEntry(e);
        myIndexEntryCache.put(indexOffset, e);
      }
      return e;
    }

    private CommittedChangeList getChangeListAtOffset(final long offset) throws IOException {
      CommittedChangeList changeList = myPreviousChangeListsCache.get(offset);
      if (changeList == null) {
        changeList = myChangesCacheFile.loadChangeListAt(offset);
        myPreviousChangeListsCache.put(offset, changeList);
      }
      return changeList; 
    }

    private static boolean isDeletedFile(final Set<FilePath> deletedFiles,
                                         final ContentRevision afterRevision,
                                         final Set<FilePath> replacedFiles) {
      FilePath file = afterRevision.getFile();
      while(file != null) {
        if (deletedFiles.contains(file)) {
          return true;
        }
        file = file.getParentPath();
        if (file != null && replacedFiles.contains(file)) {
          return true;
        }
      }
      return false;
    }
  }

  private static class IndexEntry {
    long number;
    long date;
    long offset;
    boolean completelyDownloaded;
  }

  private static class IncomingChangeListData {
    public long indexOffset;
    public IndexEntry indexEntry;
    public CommittedChangeList changeList;
    public Set<Change> accountedChanges;

    List<Change> getChangesToProcess() {
      return ContainerUtil.filter(changeList.getChanges(), new Condition<Change>() {
        @Override
        public boolean value(Change change) {
          return !accountedChanges.contains(change);
        }
      });
    }
  }

  private static final IndexEntry[] NO_ENTRIES = new IndexEntry[0];

  private static class VersionMismatchException extends RuntimeException {
  }

  private static class ReceivedChangeListTracker {
    private final Map<CommittedChangeList, ReceivedChangeList> myMap = new HashMap<>();

    public void addChange(CommittedChangeList changeList, Change change) {
      ReceivedChangeList list = myMap.get(changeList);
      if (list == null) {
        list = new ReceivedChangeList(changeList);
        myMap.put(changeList, list);
      }
      list.addChange(change);
    }

    public Collection<? extends CommittedChangeList> getChangeLists() {
      return myMap.values();
    }
  }
}
