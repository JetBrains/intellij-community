// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.vcs.MockContentRevision;
import com.intellij.util.AsynchConsumer;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

public class MockCommittedChangesProvider implements CachingCommittedChangesProvider<CommittedChangeListImpl, ChangeBrowserSettings> {
  private final List<CommittedChangeListImpl> myChangeLists = new ArrayList<>();
  private int myRefreshCount = 0;

  @NotNull
  @Override
  public ChangesBrowserSettingsEditor<ChangeBrowserSettings> createFilterUI(boolean showDateFilter) {
    return new StandardVersionFilterComponent<>(showDateFilter) {
      @NotNull
      @Override
      public JComponent getComponent() {
        return (JComponent)getStandardPanel();
      }
    };
  }

  @NotNull
  @Override
  public RepositoryLocation getLocationFor(@NotNull FilePath root) {
    return new DefaultRepositoryLocation(root.getPath());
  }

  @NotNull
  @Override
  public List<CommittedChangeListImpl> getCommittedChanges(ChangeBrowserSettings settings, RepositoryLocation location, int maxCount) {
    myRefreshCount++;
    return myChangeLists;
  }

  @Override
  public void loadCommittedChanges(ChangeBrowserSettings settings,
                                   @NotNull RepositoryLocation location,
                                   int maxCount,
                                   @NotNull AsynchConsumer<? super CommittedChangeList> consumer) {
    ++myRefreshCount;
    for (CommittedChangeListImpl changeList : myChangeLists) {
      consumer.consume(changeList);
    }
    consumer.finished();
  }

  @NotNull
  @Override
  public Pair<CommittedChangeListImpl, FilePath> getOneList(@NotNull VirtualFile file, VcsRevisionNumber number) {
    ++myRefreshCount;
    return new Pair<>(myChangeLists.get(0), VcsUtil.getFilePath(file));
  }

  public int getRefreshCount() {
    return myRefreshCount;
  }

  @Override
  public ChangeListColumn @NotNull [] getColumns() {
    return new ChangeListColumn[0];
  }

  @Override
  public int getUnlimitedCountValue() {
    return 0;
  }

  public CommittedChangeList registerChangeList(final String name, final Change... changes) {
    final CommittedChangeListImpl list = createList(name, "user", name, new Date().getTime(), 1, changes);
    myChangeLists.add(list);
    return list;
  }

  @NotNull
  private static CommittedChangeListImpl createList(String name, String author, String comment, long date, long number, Change... changes) {
    final Collection<Change> changeList = new ArrayList<>();
    Collections.addAll(changeList, changes);
    return new CommittedChangeListImpl(name, comment, author, number, new Date(date), changeList);
  }

  @Override
  public int getFormatVersion() {
    return 0;
  }

  @Override
  public void writeChangeList(@NotNull DataOutput stream, @NotNull CommittedChangeListImpl list) throws IOException {
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

  @NotNull
  @Override
  public CommittedChangeListImpl readChangeList(@NotNull RepositoryLocation location, @NotNull DataInput stream) throws IOException {
    final String name = stream.readUTF();
    int changeCount = stream.readInt();
    final String author = stream.readUTF();
    final String comment = stream.readUTF();
    final long date = stream.readLong();
    final long number = stream.readLong();

    final Change[] changes = new Change[changeCount];
    for (int i = 0; i < changeCount; i++) {
      int changeType = stream.readByte();
      String path = stream.readUTF();
      int revision = stream.readInt();
      changes[i] = switch (changeType) {
        case 0 -> createMockDeleteChange(path, revision);
        case 2 -> createMockCreateChange(path, revision);
        default -> createMockChange(path, revision);
      };
    }
    return createList(name, author, comment, date, number, changes);
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
