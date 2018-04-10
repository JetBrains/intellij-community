// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowserSettingsEditor;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.vcs.MockContentRevision;
import com.intellij.util.AsynchConsumer;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

/**
 * @author yole
 */
public class MockCommittedChangesProvider implements CachingCommittedChangesProvider<CommittedChangeListImpl, ChangeBrowserSettings> {
  private final List<CommittedChangeListImpl> myChangeLists = new ArrayList<>();
  private int myRefreshCount = 0;

  @Override
  public ChangesBrowserSettingsEditor createFilterUI(final boolean showDateFilter) {
    return null;
  }

  @Override
  public RepositoryLocation getLocationFor(FilePath root) {
    return new DefaultRepositoryLocation(root.getPath());
  }

  @Nullable
  @Override
  public VcsCommittedListsZipper getZipper() {
    return null;
  }

  @Override
  public List<CommittedChangeListImpl> getCommittedChanges(ChangeBrowserSettings settings, RepositoryLocation location, final int maxCount) {
    myRefreshCount++;
    return myChangeLists;
  }

  @Override
  public void loadCommittedChanges(ChangeBrowserSettings settings,
                                   RepositoryLocation location,
                                   int maxCount,
                                   AsynchConsumer<CommittedChangeList> consumer) {
    ++ myRefreshCount;
    for (CommittedChangeListImpl changeList : myChangeLists) {
      consumer.consume(changeList);
    }
    consumer.finished();
  }

  @Override
  public Pair<CommittedChangeListImpl, FilePath> getOneList(VirtualFile file, VcsRevisionNumber number) {
    ++ myRefreshCount;
    return new Pair<>(myChangeLists.get(0), VcsUtil.getFilePath(file));
  }

  @Override
  public RepositoryLocation getForNonLocal(VirtualFile file) {
    return null;
  }

  @Override
  public boolean supportsIncomingChanges() {
    return true;
  }

  public int getRefreshCount() {
    return myRefreshCount;
  }

  @Override
  public ChangeListColumn[] getColumns() {
    return new ChangeListColumn[0];
  }

  @Override
  @Nullable
  public VcsCommittedViewAuxiliary createActions(final DecoratorManager manager, final RepositoryLocation location) {
    return null;
  }

  @Override
  public int getUnlimitedCountValue() {
    return 0;
  }

  public CommittedChangeList registerChangeList(final String name, final Change... changes) {
    final CommittedChangeListImpl list = createList(name, "user",name, new Date().getTime(), 1, changes);
    myChangeLists.add(list);
    return list;
  }

  private static CommittedChangeListImpl createList(final String name, final String author, final String comment,
                                                    final long date, final long number, final Change... changes) {
    final Collection<Change> changeList = new ArrayList<>();
    Collections.addAll(changeList, changes);
    return new CommittedChangeListImpl(name, comment, author, number, new Date(date), changeList);
  }

  @Override
  public int getFormatVersion() {
    return 0;
  }

  @Override
  public void writeChangeList(final DataOutput stream, final CommittedChangeListImpl list) throws IOException {
    stream.writeUTF(list.getName());
    stream.writeInt(list.getChanges().size());
    stream.writeUTF(list.getCommitterName());
    stream.writeUTF(list.getComment());
    stream.writeLong(list.getCommitDate().getTime());
    stream.writeLong(list.getNumber());

    for(Change c: list.getChanges()) {
      ContentRevision revision = c.getAfterRevision();
      if (revision == null) {
        stream.writeByte(0);
        revision = c.getBeforeRevision();
      }
      else {
        stream.writeByte(c.getBeforeRevision() != null ? 1 : 2);
      }
      VcsRevisionNumber.Int revisionNumber = (VcsRevisionNumber.Int) revision.getRevisionNumber();
      stream.writeUTF(revision.getFile().getIOFile().getPath());
      stream.writeInt(revisionNumber.getValue());
    }
  }

  @Override
  public CommittedChangeListImpl readChangeList(final RepositoryLocation location, final DataInput stream) throws IOException {
    final String name = stream.readUTF();
    int changeCount = stream.readInt();
    final String author = stream.readUTF();
    final String comment = stream.readUTF();
    final long date = stream.readLong();
    final long number = stream.readLong();

    final Change[] changes = new Change[changeCount];
    for(int i=0; i<changeCount; i++) {
      int changeType = stream.readByte();
      String path = stream.readUTF();
      int revision = stream.readInt();
      switch(changeType) {
        case 0:
          changes [i] = createMockDeleteChange(path, revision);
          break;
        case 2:
          changes [i] = createMockCreateChange(path, revision);
          break;
        default:
          changes [i] = createMockChange(path, revision);
      }
    }
    return createList(name, author, comment, date, number, changes);
  }

  @Override
  public boolean isMaxCountSupported() {
    return true;
  }

  @Override
  public Collection<FilePath> getIncomingFiles(final RepositoryLocation location) {
    return null;
  }

  @Override
  public boolean refreshCacheByNumber() {
    return false;
  }

  @Override
  public String getChangelistTitle() {
    return null;
  }

  @Override
  public boolean isChangeLocallyAvailable(final FilePath filePath,
                                          @Nullable final VcsRevisionNumber localRevision, final VcsRevisionNumber changeRevision,
                                          final CommittedChangeListImpl changeList) {
    return localRevision != null && localRevision.compareTo(changeRevision) >= 0;
  }

  @Override
  public boolean refreshIncomingWithCommitted() {
    return false;
  }

  public static Change createMockMovedChange(final String pathBefore, final String pathAfter, final int revision) {
    final FilePath fullPath = VcsUtil.getFilePath(pathBefore, false);
    final FilePath fullPath2 = VcsUtil.getFilePath(pathAfter, false);

    final ContentRevision beforeRevision = new MockContentRevision(fullPath, new VcsRevisionNumber.Int(revision-1));
    final ContentRevision afterRevision = new MockContentRevision(fullPath2, new VcsRevisionNumber.Int(revision));
    return new Change(beforeRevision, afterRevision);
  }

  public static Change createMockChange(final String fullPathName, final int revision) {
    final FilePath fullPath = VcsUtil.getFilePath(fullPathName, false);
    final ContentRevision beforeRevision = new MockContentRevision(fullPath, new VcsRevisionNumber.Int(revision-1));
    final ContentRevision afterRevision = new MockContentRevision(fullPath, new VcsRevisionNumber.Int(revision));
    return new Change(beforeRevision, afterRevision);
  }

  public static Change createMockDeleteChange(final String fullPathName, final int revision) {
    final FilePath fullPath = VcsUtil.getFilePath(fullPathName, false);
    final ContentRevision beforeRevision = new MockContentRevision(fullPath, new VcsRevisionNumber.Int(revision));
    return new Change(beforeRevision, null);
  }

  public static Change createMockCreateChange(final String fullPathName, final int revision) {
    final FilePath fullPath = VcsUtil.getFilePath(fullPathName, false);
    final ContentRevision afterRevision = new MockContentRevision(fullPath, new VcsRevisionNumber.Int(revision));
    return new Change(null, afterRevision);
  }
}
