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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;

/**
 * @author yole
 */
public class ChangesCacheFile {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.committed.ChangesCacheFile");
  private static final int VERSION = 6;

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
  private long myFirstCachedChangelist = Long.MAX_VALUE;
  private long myLastCachedChangelist = -1;
  private int myIncomingCount = 0;
  private boolean myHaveCompleteHistory = false;
  private boolean myHeaderLoaded = false;
  @NonNls private static final String INDEX_EXTENSION = ".index";
  private static final int INDEX_ENTRY_SIZE = 3*8+2;
  private static final int HEADER_SIZE = 46;

  public ChangesCacheFile(Project project, File path, AbstractVcs vcs, VirtualFile root, RepositoryLocation location) {
    final Calendar date = Calendar.getInstance();
    date.set(2020, 1, 2);
    myFirstCachedDate = date.getTime();
    date.set(1970, 1, 2);
    myLastCachedDate = date.getTime();
    myProject = project;
    myPath = path;
    myIndexPath = new File(myPath.toString() + INDEX_EXTENSION);
    myVcs = vcs;
    myChangesProvider = (CachingCommittedChangesProvider) vcs.getCommittedChangesProvider();
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myRootPath = new FilePathImpl(root);
    myLocation = location;
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

  public List<CommittedChangeList> writeChanges(final List<CommittedChangeList> changes) throws IOException {
    List<CommittedChangeList> result = new ArrayList<CommittedChangeList>(changes.size());
    boolean wasEmpty = isEmpty();
    openStreams();
    try {
      if (wasEmpty) {
        myHeaderLoaded = true;
        writeHeader();
      }
      myStream.seek(myStream.length());
      IndexEntry[] entries = readLastIndexEntries(0, changes.size());
      // the list and index are sorted in direct chronological order
      Collections.sort(changes, new Comparator<CommittedChangeList>() {
        public int compare(final CommittedChangeList o1, final CommittedChangeList o2) {
          return Comparing.compare(o1.getCommitDate(), o2.getCommitDate());
        }
      });
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
        writeIndexEntry(list.getNumber(), list.getCommitDate().getTime(), position, false);
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
      myStream.close();
    }
    finally {
      myIndexStream.close();
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

      final List<CommittedChangeList> result = new ArrayList<CommittedChangeList>();
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
    final List<CommittedChangeList> result = new ArrayList<CommittedChangeList>();
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
    List<CommittedChangeList> result = new ArrayList<CommittedChangeList>();
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
          saveIncoming(data);
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

  private void saveIncoming(final IncomingChangeListData data) throws IOException {
    writePartial(data);
    if (data.accountedChanges.size() == data.changeList.getChanges().size()) {
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
      FilePath path = new FilePathImpl(new File(file), false);
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
    List<IncomingChangeListData> incomingData = new ArrayList<IncomingChangeListData>();
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

  private void writePartial(final IncomingChangeListData data) throws IOException {
    File partialFile = getPartialPath(data.indexEntry.offset);
    final int accounted = data.accountedChanges.size();
    if (accounted == data.changeList.getChanges().size()) {
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
    HashSet<Change> result = new HashSet<Change>();
    try {
      File partialFile = getPartialPath(data.indexEntry.offset);
      if (partialFile.exists()) {
        RandomAccessFile file = new RandomAccessFile(partialFile, "r");
        try {
          int count = file.readInt();
          for(int i=0; i<count; i++) {
            boolean isAfterRevision = (file.readByte() != 0);
            String path = file.readUTF();
            for(Change c: data.changeList.getChanges()) {
              final ContentRevision afterRevision = isAfterRevision ? c.getAfterRevision() : c.getBeforeRevision();
              if (afterRevision != null && afterRevision.getFile().getIOFile().toString().equals(path)) {
                result.add(c);
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
    return new RefreshIncomingChangesOperation().invoke();
  }

  public AbstractVcs getVcs() {
    return myVcs;
  }

  public FilePath getRootPath() {
    return myRootPath;
  }

  private class RefreshIncomingChangesOperation {
    private FactoryMap<VirtualFile, VcsRevisionNumber> myCurrentRevisions;
    private Set<FilePath> myDeletedFiles;
    private Set<FilePath> myCreatedFiles;
    private Set<FilePath> myReplacedFiles;
    private final Map<Long, IndexEntry> myIndexEntryCache = new HashMap<Long, IndexEntry>();
    private final Map<Long, CommittedChangeList> myPreviousChangeListsCache = new HashMap<Long, CommittedChangeList>();
    private List<LocalChangeList> myChangeLists;
    private ChangeListManagerImpl myClManager;

    public boolean invoke() throws VcsException, IOException {
      if (myProject.isDisposed()) {
        return false;
      }
      myClManager = ChangeListManagerImpl.getInstanceImpl(myProject);
      final DiffProvider diffProvider = myVcs.getDiffProvider();
      if (diffProvider == null) return false;

      myLocation.onBeforeBatch();
      final Collection<FilePath> incomingFiles = myChangesProvider.getIncomingFiles(myLocation);
      boolean anyChanges = false;
      openStreams();
      loadHeader();
      myCurrentRevisions = new FactoryMap<VirtualFile, VcsRevisionNumber>() {
        protected VcsRevisionNumber create(final VirtualFile key) {
          return diffProvider.getCurrentRevision(key);
        }
      };
      try {
        final List<IncomingChangeListData> list = loadIncomingChangeListData();
        // the incoming changelist pointers are actually sorted in reverse chronological order,
        // so we process file delete changes before changes made to deleted files before they were deleted
        myDeletedFiles = new HashSet<FilePath>();
        myCreatedFiles = new HashSet<FilePath>();
        myReplacedFiles = new HashSet<FilePath>();
        IncomingChangeState.header(myLocation.toPresentableString());
        for(IncomingChangeListData data: list) {
          debug("Checking incoming changelist " + data.changeList.getNumber());
          boolean updated = false;
          for(Change change: data.changeList.getChanges()) {
            if (data.accountedChanges.contains(change)) continue;
            final ContentRevision revision = (change.getAfterRevision() == null) ? change.getBeforeRevision() : change.getAfterRevision();
            final IncomingChangeState state = new IncomingChangeState(change, revision.getRevisionNumber().asString());
            final boolean changeFound = processIncomingChange(change, data, incomingFiles, state);
            state.logSelf();
            if (changeFound) {
              data.accountedChanges.add(change);
            }
            updated |= changeFound;
          }
          if (updated) {
            anyChanges = true;
            saveIncoming(data);
          }
        }
        IncomingChangeState.footer();
        if (anyChanges) {
          writeHeader();
        }
      }
      finally {
        myLocation.onAfterBatch();
        closeStreams();
      }
      return anyChanges;
    }

    private boolean processIncomingChange(final Change change,
                                          final IncomingChangeListData changeListData,
                                          @Nullable final Collection<FilePath> incomingFiles, final IncomingChangeState state) throws IOException {
      CommittedChangeList changeList = changeListData.changeList;
      ContentRevision afterRevision = change.getAfterRevision();
      if (afterRevision != null) {
        if (afterRevision.getFile().isNonLocal()) {
          // don't bother to search for nonlocal paths on local disk
          state.setState(IncomingChangeState.State.AFTER_DOES_NOT_MATTER_NON_LOCAL);
          return true;
        }
        if (change.getBeforeRevision() == null) {
          final FilePath path = afterRevision.getFile();
          debug("Marking created file " + path);
          myCreatedFiles.add(path);
        } else if (change.getBeforeRevision().getFile().getIOFile().getAbsolutePath().equals(
          afterRevision.getFile().getIOFile().getAbsolutePath()) && change.isIsReplaced()) {
          myReplacedFiles.add(afterRevision.getFile());
        }
        if (incomingFiles != null && !incomingFiles.contains(afterRevision.getFile())) {
          debug("Skipping new/changed file outside of incoming files: " + afterRevision.getFile());
          state.setState(IncomingChangeState.State.AFTER_DOES_NOT_MATTER_OUTSIDE_INCOMING);
          return true;
        }
        debug("Checking file " + afterRevision.getFile().getPath());
        FilePath localPath = ChangesUtil.getLocalPath(myProject, afterRevision.getFile());

        if (! FileUtil.isAncestor(myRootPath.getIOFile(), localPath.getIOFile(), false)) {
          // alien change in list; skip
          debug("Alien path " + localPath.getPresentableUrl() + " under root " + myRootPath.getPresentableUrl() + "; skipping.");
          state.setState(IncomingChangeState.State.AFTER_DOES_NOT_MATTER_ALIEN_PATH);
          return true;
        }

        localPath.refresh();
        VirtualFile file = localPath.getVirtualFile();
        if (isDeletedFile(myDeletedFiles, afterRevision, myReplacedFiles)) {
          debug("Found deleted file");
          state.setState(IncomingChangeState.State.AFTER_DOES_NOT_MATTER_DELETED_FOUND_IN_INCOMING_LIST);
          return true;
        }
        else if (file != null) {
          VcsRevisionNumber revision = myCurrentRevisions.get(file);
          if (revision != null) {
            debug("Current revision is " + revision + ", changelist revision is " + afterRevision.getRevisionNumber());
            //noinspection unchecked
            if (myChangesProvider.isChangeLocallyAvailable(afterRevision.getFile(), revision, afterRevision.getRevisionNumber(), changeList)) {
              state.setState(IncomingChangeState.State.AFTER_EXISTS_LOCALLY_AVAILABLE);
              return true;
            } else {
              state.setState(IncomingChangeState.State.AFTER_EXISTS_NOT_LOCALLY_AVAILABLE);
              return false;
            }
          }
          else {
            debug("Failed to fetch revision");
            state.setState(IncomingChangeState.State.AFTER_EXISTS_REVISION_NOT_LOADED);
            return false;
          }
        }
        else {
          //noinspection unchecked
          if (myChangesProvider.isChangeLocallyAvailable(afterRevision.getFile(), null, afterRevision.getRevisionNumber(), changeList)) {
            state.setState(IncomingChangeState.State.AFTER_NOT_EXISTS_LOCALLY_AVAILABLE);
            return true;
          }
          if (fileMarkedForDeletion(localPath)) {
            debug("File marked for deletion and not committed jet.");
            state.setState(IncomingChangeState.State.AFTER_NOT_EXISTS_MARKED_FOR_DELETION);
            return true;
          }
          if (wasSubsequentlyDeleted(afterRevision.getFile(), changeListData.indexOffset)) {
            state.setState(IncomingChangeState.State.AFTER_NOT_EXISTS_SUBSEQUENTLY_DELETED);
            return true;
          }
          debug("Could not find local file for change " + afterRevision.getFile().getPath());
          state.setState(IncomingChangeState.State.AFTER_NOT_EXISTS_OTHER);
          return false;
        }
      }
      else {
        ContentRevision beforeRevision = change.getBeforeRevision();
        assert beforeRevision != null;
        debug("Checking deleted file " + beforeRevision.getFile());
        myDeletedFiles.add(beforeRevision.getFile());
        if (incomingFiles != null && !incomingFiles.contains(beforeRevision.getFile())) {
          debug("Skipping deleted file outside of incoming files: " + beforeRevision.getFile());
          state.setState(IncomingChangeState.State.BEFORE_DOES_NOT_MATTER_OUTSIDE);
          return true;
        }
        beforeRevision.getFile().refresh();
        if (beforeRevision.getFile().getVirtualFile() == null || myCreatedFiles.contains(beforeRevision.getFile())) {
          // if not deleted from vcs, mark as incoming, otherwise file already deleted
          final boolean locallyDeleted = myClManager.isContainedInLocallyDeleted(beforeRevision.getFile());
          debug(locallyDeleted ? "File deleted locally, change marked as incoming" : "File already deleted");
          state.setState(locallyDeleted ? IncomingChangeState.State.BEFORE_NOT_EXISTS_DELETED_LOCALLY : IncomingChangeState.State.BEFORE_NOT_EXISTS_ALREADY_DELETED);
          return !locallyDeleted;
        }
        else if (!myVcs.fileExistsInVcs(beforeRevision.getFile())) {
          debug("File exists locally and is unversioned");
          state.setState(IncomingChangeState.State.BEFORE_UNVERSIONED_INSTEAD_OF_VERS_DELETED);
          return true;
        }
        else {
          final VirtualFile file = beforeRevision.getFile().getVirtualFile();
          final VcsRevisionNumber currentRevision = myCurrentRevisions.get(file);
          if ((currentRevision != null) && (currentRevision.compareTo(beforeRevision.getRevisionNumber()) > 0)) {
            // revived in newer revision - possibly was added file with same name
            debug("File with same name was added after file deletion");
            state.setState(IncomingChangeState.State.BEFORE_SAME_NAME_ADDED_AFTER_DELETION);
            return true;
          }
          state.setState(IncomingChangeState.State.BEFORE_EXISTS_BUT_SHOULD_NOT);
          debug("File exists locally and no 'create' change found for it");
        }
      }
      return false;
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
        while(indexOffset < myIndexStream.length()) {
          IndexEntry e = getIndexEntryAtOffset(indexOffset);

          final CommittedChangeList changeList = getChangeListAtOffset(e.offset);
          for(Change c: changeList.getChanges()) {
            final ContentRevision beforeRevision = c.getBeforeRevision();
            if ((beforeRevision != null) && (c.getAfterRevision() == null)) {
              if (file.getIOFile().getAbsolutePath().equals(beforeRevision.getFile().getIOFile().getAbsolutePath()) ||
                  file.isUnder(beforeRevision.getFile(), false)) {
                debug("Found subsequent deletion for file " + file);
                return true;
              }
            } else if ((beforeRevision != null) && (c.getAfterRevision() != null) &&
                       (beforeRevision.getFile().getIOFile().getAbsolutePath().equals(
                         c.getAfterRevision().getFile().getIOFile().getAbsolutePath()))) {
              if (file.isUnder(beforeRevision.getFile(), true) && c.isIsReplaced()) {
                debug("For " + file + "some of parents is replaced: " + beforeRevision.getFile());
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

    private IndexEntry getIndexEntryAtOffset(final long indexOffset) throws IOException {
      IndexEntry e = myIndexEntryCache.get(indexOffset);
      if (e == null) {
        myIndexStream.seek(indexOffset);
        e = new IndexEntry();
        readIndexEntry(e);
        myIndexEntryCache.put(indexOffset, e);
      }
      return e;
    }

    private CommittedChangeList getChangeListAtOffset(final long offset) throws IOException {
      CommittedChangeList changeList = myPreviousChangeListsCache.get(offset);
      if (changeList == null) {
        changeList = loadChangeListAt(offset);
        myPreviousChangeListsCache.put(offset, changeList);
      }
      return changeList; 
    }

    private boolean isDeletedFile(final Set<FilePath> deletedFiles, final ContentRevision afterRevision, final Set<FilePath> replacedFiles) {
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
  }

  private static final IndexEntry[] NO_ENTRIES = new IndexEntry[0];

  private static class VersionMismatchException extends RuntimeException {
  }

  private static class ReceivedChangeListTracker {
    private final Map<CommittedChangeList, ReceivedChangeList> myMap = new HashMap<CommittedChangeList, ReceivedChangeList>();

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
