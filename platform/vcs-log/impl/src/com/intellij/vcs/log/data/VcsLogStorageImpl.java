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
package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Function;
import com.intellij.util.io.*;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.FatalErrorHandler;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.VcsRefImpl;
import com.intellij.vcs.log.util.PersistentUtil;
import gnu.trove.TObjectIntHashMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Supports the int <-> Hash and int <-> VcsRef persistent mappings.
 */
public class VcsLogStorageImpl implements Disposable, VcsLogStorage {
  @NotNull private static final Logger LOG = Logger.getInstance(VcsLogStorage.class);
  @NotNull private static final String HASHES_STORAGE = "hashes";
  @NotNull private static final String REFS_STORAGE = "refs";
  @NotNull public static final VcsLogStorage EMPTY = new EmptyLogStorage();

  public static final int VERSION = 6;
  public static final int NO_INDEX = -1;
  private static final int REFS_VERSION = 1;

  @NotNull private final MyPersistentBTreeEnumerator myCommitIdEnumerator;
  @NotNull private final PersistentEnumeratorBase<VcsRef> myRefsEnumerator;
  @NotNull private final FatalErrorHandler myExceptionReporter;
  private volatile boolean myDisposed = false;

  public VcsLogStorageImpl(@NotNull Project project,
                           @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                           @NotNull FatalErrorHandler exceptionReporter,
                           @NotNull Disposable parent) throws IOException {
    myExceptionReporter = exceptionReporter;

    List<VirtualFile> roots = StreamEx.ofKeys(logProviders).sortedBy(VirtualFile::getPath).toList();

    String logId = PersistentUtil.calcLogId(project, logProviders);
    MyCommitIdKeyDescriptor commitIdKeyDescriptor = new MyCommitIdKeyDescriptor(roots);

    File storageFile = PersistentUtil.getStorageFile(HASHES_STORAGE, logId, VERSION);
    myCommitIdEnumerator = IOUtil.openCleanOrResetBroken(() -> new MyPersistentBTreeEnumerator(storageFile, commitIdKeyDescriptor),
                                                         storageFile);
    myRefsEnumerator = PersistentUtil.createPersistentEnumerator(new VcsRefKeyDescriptor(logProviders, commitIdKeyDescriptor),
                                                                 REFS_STORAGE, logId, VERSION + REFS_VERSION);
    Disposer.register(parent, this);
  }

  @NotNull
  public static Function<Integer, Hash> createHashGetter(@NotNull VcsLogStorage storage) {
    return commitIndex -> {
      CommitId commitId = storage.getCommitId(commitIndex);
      if (commitId == null) return null;
      return commitId.getHash();
    };
  }

  @Nullable
  private CommitId doGetCommitId(int index) throws IOException {
    return myCommitIdEnumerator.valueOf(index);
  }

  private int getOrPut(@NotNull Hash hash, @NotNull VirtualFile root) throws IOException {
    return myCommitIdEnumerator.enumerate(new CommitId(hash, root));
  }

  @Override
  public int getCommitIndex(@NotNull Hash hash, @NotNull VirtualFile root) {
    checkDisposed();
    try {
      return getOrPut(hash, root);
    }
    catch (IOException e) {
      myExceptionReporter.consume(this, e);
    }
    return NO_INDEX;
  }

  @Override
  @Nullable
  public CommitId getCommitId(int commitIndex) {
    checkDisposed();
    try {
      CommitId commitId = doGetCommitId(commitIndex);
      if (commitId == null) {
        myExceptionReporter.consume(this, new RuntimeException("Unknown commit index: " + commitIndex));
      }
      return commitId;
    }
    catch (IOException e) {
      myExceptionReporter.consume(this, e);
    }
    return null;
  }

  @Override
  public boolean containsCommit(@NotNull CommitId id) {
    checkDisposed();
    try {
      return myCommitIdEnumerator.contains(id);
    }
    catch (IOException e) {
      myExceptionReporter.consume(this, e);
    }
    return false;
  }

  @Override
  public void iterateCommits(@NotNull Function<CommitId, Boolean> consumer) {
    checkDisposed();
    try {
      myCommitIdEnumerator.iterateData(new CommonProcessors.FindProcessor<CommitId>() {
        @Override
        protected boolean accept(CommitId commitId) {
          return consumer.fun(commitId);
        }
      });
    }
    catch (IOException e) {
      myExceptionReporter.consume(this, e);
    }
  }

  @Override
  public int getRefIndex(@NotNull VcsRef ref) {
    checkDisposed();
    try {
      return myRefsEnumerator.enumerate(ref);
    }
    catch (IOException e) {
      myExceptionReporter.consume(this, e);
    }
    return NO_INDEX;
  }

  @Nullable
  @Override
  public VcsRef getVcsRef(int refIndex) {
    checkDisposed();
    try {
      return myRefsEnumerator.valueOf(refIndex);
    }
    catch (IOException e) {
      myExceptionReporter.consume(this, e);
      return null;
    }
  }

  public void flush() {
    checkDisposed();
    myCommitIdEnumerator.force();
    myRefsEnumerator.force();
  }

  @Override
  public void dispose() {
    try {
      myDisposed = true;
      myCommitIdEnumerator.close();
      myRefsEnumerator.close();
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  private void checkDisposed() {
    if (myDisposed) throw new ProcessCanceledException();
  }

  private static class MyCommitIdKeyDescriptor implements KeyDescriptor<CommitId> {
    @NotNull private final List<VirtualFile> myRoots;
    @NotNull private final TObjectIntHashMap<VirtualFile> myRootsReversed;

    public MyCommitIdKeyDescriptor(@NotNull List<VirtualFile> roots) {
      myRoots = roots;

      myRootsReversed = new TObjectIntHashMap<>();
      for (int i = 0; i < roots.size(); i++) {
        myRootsReversed.put(roots.get(i), i);
      }
    }

    @Override
    public void save(@NotNull DataOutput out, CommitId value) throws IOException {
      ((HashImpl)value.getHash()).write(out);
      out.writeInt(myRootsReversed.get(value.getRoot()));
    }

    @Override
    public CommitId read(@NotNull DataInput in) throws IOException {
      Hash hash = HashImpl.read(in);
      VirtualFile root = myRoots.get(in.readInt());
      if (root == null) return null;
      return new CommitId(hash, root);
    }

    @Override
    public int getHashCode(CommitId value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(CommitId val1, CommitId val2) {
      return val1.equals(val2);
    }
  }

  private static class EmptyLogStorage implements VcsLogStorage {
    @Override
    public int getCommitIndex(@NotNull Hash hash, @NotNull VirtualFile root) {
      return 0;
    }

    @NotNull
    @Override
    public CommitId getCommitId(int commitIndex) {
      throw new UnsupportedOperationException("Illegal access to empty hash map by index " + commitIndex);
    }

    @Override
    public boolean containsCommit(@NotNull CommitId id) {
      return false;
    }

    @Override
    public void iterateCommits(@NotNull Function<CommitId, Boolean> consumer) {
    }

    @Override
    public int getRefIndex(@NotNull VcsRef ref) {
      return 0;
    }

    @Nullable
    @Override
    public VcsRef getVcsRef(int refIndex) {
      throw new UnsupportedOperationException("Illegal access to empty ref map by index " + refIndex);
    }

    @Override
    public void flush() {
    }
  }

  private static class VcsRefKeyDescriptor implements KeyDescriptor<VcsRef> {
    @NotNull private final Map<VirtualFile, VcsLogProvider> myLogProviders;
    @NotNull private final KeyDescriptor<CommitId> myCommitIdKeyDescriptor;

    public VcsRefKeyDescriptor(@NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                               @NotNull KeyDescriptor<CommitId> commitIdKeyDescriptor) {
      myLogProviders = logProviders;
      myCommitIdKeyDescriptor = commitIdKeyDescriptor;
    }

    @Override
    public int getHashCode(@NotNull VcsRef value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(@NotNull VcsRef val1, @NotNull VcsRef val2) {
      return val1.equals(val2);
    }

    @Override
    public void save(@NotNull DataOutput out, @NotNull VcsRef value) throws IOException {
      myCommitIdKeyDescriptor.save(out, new CommitId(value.getCommitHash(), value.getRoot()));
      IOUtil.writeUTF(out, value.getName());
      myLogProviders.get(value.getRoot()).getReferenceManager().serialize(out, value.getType());
    }

    @Override
    public VcsRef read(@NotNull DataInput in) throws IOException {
      CommitId commitId = myCommitIdKeyDescriptor.read(in);
      if (commitId == null) throw new IOException("Can not read commit id for reference");
      String name = IOUtil.readUTF(in);
      VcsRefType type = myLogProviders.get(commitId.getRoot()).getReferenceManager().deserialize(in);
      return new VcsRefImpl(commitId.getHash(), name, type, commitId.getRoot());
    }
  }

  private static class MyPersistentBTreeEnumerator extends PersistentBTreeEnumerator<CommitId> {
    public MyPersistentBTreeEnumerator(File storageFile, MyCommitIdKeyDescriptor commitIdKeyDescriptor) throws IOException {
      super(storageFile, commitIdKeyDescriptor, Page.PAGE_SIZE, null, VERSION);
    }

    public boolean contains(@NotNull CommitId id) throws IOException {
      return tryEnumerate(id) != NULL_ID;
    }
  }
}
