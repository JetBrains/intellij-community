package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.cassandra.CassandraIndexTable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.impl.FileNameCache;
import com.intellij.util.BitUtil;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.PersistentStringEnumerator;
import com.twelvemonkeys.io.FileUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.stream.Stream;

public class IndexerFSRecords implements IFSRecords {

  private final FSRecords myDelegate;
  private int myDelegateStartId;

  public IndexerFSRecords(FSRecords delegate) {
    myDelegate = delegate;
  }

  public FSRecords getDelegate() {
    return myDelegate;
  }

  public Stream<Integer> recordsStream() {
    int maxId = myDelegate.getMaxId();
    return Stream.iterate(myDelegateStartId, integer -> integer + 1)
      .limit(maxId - myDelegateStartId + 1)
      .filter(id -> !BitUtil.isSet(getFlags(id), FSRecords.FREE_RECORD_FLAG));
  }

  public void dumpToCassandra(int shardId) {
    CassandraIndexTable cit = CassandraIndexTable.getInstance();
    RootRecord[] roots = myDelegate.listRoots();
    for (RootRecord root : roots) {
      cit.addFsRoot(root.url, shardId, 1, root.id);
    }

    cit.bulkInsertFs(shardId, 1,
                     recordsStream()
                       .map(i -> new FSRecordsSource.RecordInfo(i,
                                                                getName(i),
                                                                getTimestamp(i),
                                                                getLength(i),
                                                                getFlags(i),
                                                                getParent(i),
                                                                getParents(i, id -> false))));

    cit.bulkInsertContents(shardId,
                           recordsStream()
                             .map(i -> {
                               try {
                                 DataInputStream stream = readContent(i);
                                 return stream != null ? Pair.create(i, ByteBuffer.wrap(FileUtil.read(stream))) : null;
                               }
                               catch (IOException e) {
                                 throw new RuntimeException(e);
                               }
                             })
                             .filter(p -> p != null));

    cit.bulkInsertAttrs(shardId, recordsStream().flatMap(i -> {
      try {
        Map<String, InputStream> attrsMap = myDelegate.listAttrs(i);
        if (attrsMap == null) {
          return Stream.empty();
        } else {
          return attrsMap.entrySet().stream().map(entry -> {
            try {
              return new CassandraIndexTable.AttrInfo(i, entry.getKey(), ByteBuffer.wrap(FileUtil.read(entry.getValue())));
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          });
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }));
  }

  @Override
  public void connect(PagedFileStorage.StorageLockContext lockContext,
                      PersistentStringEnumerator names,
                      FileNameCache fileNameCache,
                      VfsDependentEnum<String> attrsList) {
    myDelegate.connect(lockContext, names, fileNameCache, attrsList);
    myDelegateStartId = myDelegate.getMaxId();
  }

  @Override
  public void writeAttributesToRecord(int id, int parentId, @NotNull FileAttributes attributes, @NotNull String name) {
    myDelegate.writeAttributesToRecord(id, parentId, attributes, name);
  }

  @Override
  public void force() {
    myDelegate.force();
  }

  @Override
  public boolean isDirty() {
    return myDelegate.isDirty();
  }

  @Override
  public long getTimestamp() {
    return myDelegate.getTimestamp();
  }

  @Override
  public void requestRebuild(@NotNull Throwable e) throws RuntimeException, Error {
    myDelegate.requestRebuild(e);
  }

  @Override
  public void requestRebuild(int fileId, @NotNull Throwable e) throws RuntimeException, Error {
    myDelegate.requestRebuild(fileId, e);
  }

  @Override
  public long getCreationTimestamp() {
    return myDelegate.getCreationTimestamp();
  }

  @Override
  public int createChildRecord(int parentId) {
    return myDelegate.createChildRecord(parentId);
  }

  @Override
  public void deleteRecordRecursively(int id) {
    myDelegate.deleteRecordRecursively(id);
  }

  @NotNull
  @Override
  public RootRecord[] listRoots() {
    return myDelegate.listRoots();
  }

  @Override
  public int findRootRecord(@NotNull String rootUrl) {
    return myDelegate.findRootRecord(rootUrl);
  }

  @Override
  public void deleteRootRecord(int id) {
    myDelegate.deleteRootRecord(id);
  }

  @NotNull
  @Override
  public int[] list(int id) {
    return myDelegate.list(id);
  }

  @NotNull
  @Override
  public NameId[] listAll(int parentId) {
    return myDelegate.listAll(parentId);
  }

  @Override
  public boolean wereChildrenAccessed(int id) {
    return myDelegate.wereChildrenAccessed(id);
  }

  @Override
  public void updateList(int id, @NotNull int[] childIds) {
    myDelegate.updateList(id, childIds);
  }

  @Override
  public int getLocalModCount() {
    return myDelegate.getLocalModCount();
  }

  @Override
  public int getModCount() {
    return myDelegate.getModCount();
  }

  @NotNull
  @Override
  public TIntArrayList getParents(int id, @NotNull IntPredicate cached) {
    return myDelegate.getParents(id, cached);
  }

  @Override
  public void setParent(int id, int parentId) {
    myDelegate.setParent(id, parentId);
  }

  @Override
  public int getParent(int id) {
    return myDelegate.getParent(id);
  }

  @Override
  public int getNameId(int id) {
    return myDelegate.getNameId(id);
  }

  @Override
  public int getNameId(String name) {
    return myDelegate.getNameId(name);
  }

  @Override
  public String getName(int id) {
    return myDelegate.getName(id);
  }

  @NotNull
  @Override
  public CharSequence getNameSequence(int id) {
    return myDelegate.getNameSequence(id);
  }

  @Override
  public void setName(int id, @NotNull String name) {
    myDelegate.setName(id, name);
  }

  @Override
  public int getFlags(int id) {
    return myDelegate.getFlags(id);
  }

  @Override
  public void setFlags(int id, int flags, boolean markAsChange) {
    myDelegate.setFlags(id, flags, markAsChange);
  }

  @Override
  public long getLength(int id) {
    return myDelegate.getLength(id);
  }

  @Override
  public void setLength(int id, long len) {
    myDelegate.setLength(id, len);
  }

  @Override
  public long getTimestamp(int id) {
    return myDelegate.getTimestamp(id);
  }

  @Override
  public void setTimestamp(int id, long value) {
    myDelegate.setTimestamp(id, value);
  }

  @Override
  public int getModCount(int id) {
    return myDelegate.getModCount(id);
  }

  @Nullable
  @Override
  public DataInputStream readContent(int fileId) {
    return myDelegate.readContent(fileId);
  }

  @Nullable
  @Override
  public DataInputStream readContentById(int contentId) {
    return myDelegate.readContentById(contentId);
  }

  @Nullable
  @Override
  public DataInputStream readAttribute(int fileId, FileAttribute att) {
    return myDelegate.readAttribute(fileId, att);
  }

  @Override
  public int acquireFileContent(int fileId) {
    return myDelegate.acquireFileContent(fileId);
  }

  @Override
  public void releaseContent(int contentId) {
    myDelegate.releaseContent(contentId);
  }

  @Override
  public int getContentId(int fileId) {
    return myDelegate.getContentId(fileId);
  }

  @NotNull
  @Override
  public DataOutputStream writeContent(int fileId, boolean fixedSize) {
    return myDelegate.writeContent(fileId, fixedSize);
  }

  @Override
  public void writeContent(int fileId, ByteSequence bytes, boolean fixedSize) {
    myDelegate.writeContent(fileId, bytes, fixedSize);
  }

  @Override
  public int storeUnlinkedContent(byte[] bytes) {
    return myDelegate.storeUnlinkedContent(bytes);
  }

  @NotNull
  @Override
  public DataOutputStream writeAttribute(int fileId, @NotNull FileAttribute att) {
    return myDelegate.writeAttribute(fileId, att);
  }

  @Override
  public void writeBytes(int fileId, ByteSequence bytes, boolean preferFixedSize) throws IOException {
    myDelegate.writeBytes(fileId, bytes, preferFixedSize);
  }

  @Override
  public void dispose() {
    myDelegate.dispose();
  }

  @Override
  public void invalidateCaches() {
    myDelegate.invalidateCaches();
  }
}