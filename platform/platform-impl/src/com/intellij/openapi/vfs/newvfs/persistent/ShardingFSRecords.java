package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.impl.FileNameCache;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.PersistentStringEnumerator;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

public class ShardingFSRecords implements IFSRecords {

  private final Supplier<IFSRecords> myFactory;

  private PagedFileStorage.StorageLockContext myContext;
  private PersistentStringEnumerator myNames;
  private FileNameCache myCache;
  private final Object lock = new Object();

  private TIntObjectHashMap<IFSRecords> myShards = new TIntObjectHashMap<>();

  public ShardingFSRecords(Supplier<IFSRecords> shardFactory) {
    myFactory = shardFactory;
  }

  private IFSRecords getShard(int recordId) {
    int shardId = ((recordId < 0 ? -recordId : recordId) << 24) >> 24;
    IFSRecords shard = myShards.get(shardId);
    if (shard != null) {
      return shard;
    }
    synchronized (lock) {
      shard = myShards.get(shardId);
      if (shard != null) {
        return shard;
      }
      shard = new FSRecordsShard(shardId, myFactory.get());
      shard.connect(myContext, myNames, myCache);
      myShards.put(shardId, shard);
      return shard;
    }
  }

  private Collection<IFSRecords> getShards() {
    return ContainerUtil.map2List(myShards.getValues(), Function.ID);
  }

  private IFSRecords getAnyShard() {
    return getShard(1);
  }

  @Override
  public void writeAttributesToRecord(int id, int parentId, @NotNull FileAttributes attributes, @NotNull String name) {
    getShard(id).writeAttributesToRecord(id, parentId, attributes, name);
  }

  @Override
  public void connect(PagedFileStorage.StorageLockContext lockContext, PersistentStringEnumerator names, FileNameCache fileNameCache) {
    myContext = lockContext;
    myNames = names;
    myCache = fileNameCache;
  }

  @Override
  public void force() {
    for (IFSRecords records : getShards()) {
      records.force();
    }
  }

  @Override
  public boolean isDirty() {
    for (IFSRecords records : getShards()) {
      if (records.isDirty()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public long getTimestamp() {
    return getCreationTimestamp();
  }

  @Override
  public void handleError(@NotNull Throwable e) throws RuntimeException, Error {

  }

  @Override
  public void handleError(int fileId, @NotNull Throwable e) throws RuntimeException, Error {
    getShard(fileId).handleError(fileId, e);
  }

  @Override
  public long getCreationTimestamp() {
    if (getShards().isEmpty()) {
      return getShard(1).getCreationTimestamp();
    }
    // TODO: not thread safe!!!
    long ts = Long.MAX_VALUE;
    for (IFSRecords records : getShards()) {
      ts = Math.min(records.getCreationTimestamp(), ts);
    }
    return ts;
  }

  @Override
  public int createChildRecord(int parentId) {
    return getShard(parentId).createChildRecord(parentId);
  }

  @Override
  public void deleteRecordRecursively(int id) {
    getShard(id).deleteRecordRecursively(id);
  }

  @NotNull
  @Override
  public int[] listRoots() {
    TIntArrayList l = new TIntArrayList();
    for (IFSRecords records : getShards()) {
      l.add(records.listRoots());
    }
    return l.toNativeArray();
  }

  @Override
  public int findRootRecord(@NotNull String rootUrl) {
    for (IFSRecords records : getShards()) {
      int record = records.findRootRecord(rootUrl);
      if (record > 0) {
        return record;
      }
    }
    int record = getShard(1).findRootRecord(rootUrl);
    assert record > 0 : record;
    return record;
  }

  @Override
  public void deleteRootRecord(int id) {
    getShard(id).deleteRootRecord(id);
  }

  @NotNull
  @Override
  public int[] list(int id) {
    return getShard(id).list(id);
  }

  @NotNull
  @Override
  public NameId[] listAll(int parentId) {
    return getShard(parentId).listAll(parentId);
  }

  @Override
  public boolean wereChildrenAccessed(int id) {
    return getShard(id).wereChildrenAccessed(id);
  }

  @Override
  public void updateList(int id, @NotNull int[] childIds) {
    getShard(id).updateList(id, childIds);
  }

  @Override
  public int getLocalModCount() {
    int res = 0;
    for (IFSRecords records : getShards()) {
      res += records.getLocalModCount();
    }
    return res;
  }

  @Override
  public int getModCount() {
    int res = 0;
    for (IFSRecords records : getShards()) {
      res += records.getModCount();
    }
    return res;
  }

  @NotNull
  @Override
  public TIntArrayList getParents(int id, @NotNull IntPredicate cached) {
    return getShard(id).getParents(id, cached);
  }

  @Override
  public void setParent(int id, int parentId) {
    getShard(id).setParent(id, parentId);
  }

  @Override
  public int getNameId(int id) {
    return getShard(id).getNameId(id);
  }

  @Override
  public int getNameId(String name) {
    try {
      return myNames.enumerate(name);
    }
    catch (Throwable e) {
      handleError(e);
    }
    return -1;
  }

  @Override
  public String getName(int id) {
    return getShard(id).getName(id);
  }

  @NotNull
  @Override
  public CharSequence getNameSequence(int id) {
    return getShard(id).getNameSequence(id);
  }

  @Override
  public void setName(int id, @NotNull String name) {
    getShard(id).setName(id, name);
  }

  @Override
  public int getFlags(int id) {
    return getShard(id).getFlags(id);
  }

  @Override
  public void setFlags(int id, int flags, boolean markAsChange) {
    getShard(id).setFlags(id, flags, markAsChange);
  }

  @Override
  public long getLength(int id) {
    return getShard(id).getLength(id);
  }

  @Override
  public void setLength(int id, long len) {
    getShard(id).setLength(id, len);
  }

  @Override
  public long getTimestamp(int id) {
    return getShard(id).getTimestamp(id);
  }

  @Override
  public void setTimestamp(int id, long value) {
    getShard(id).setTimestamp(id, value);
  }

  @Override
  public int getModCount(int id) {
    return getShard(id).getModCount(id);
  }

  @Nullable
  @Override
  public DataInputStream readContent(int fileId) {
    return getShard(fileId).readContent(fileId);
  }

  @Nullable
  @Override
  public DataInputStream readContentById(int contentId) {
    return getShard(contentId).readContentById(contentId);
  }

  @Nullable
  @Override
  public DataInputStream readAttribute(int fileId, FileAttribute att) {
    return getShard(fileId).readAttribute(fileId, att);
  }

  @Override
  public int acquireFileContent(int fileId) {
    return getShard(fileId).acquireFileContent(fileId);
  }

  @Override
  public void releaseContent(int contentId) {
    getShard(contentId).releaseContent(contentId);
  }

  @Override
  public int getContentId(int fileId) {
    return getShard(fileId).getContentId(fileId);
  }

  @NotNull
  @Override
  public DataOutputStream writeContent(int fileId, boolean readOnly) {
    return getShard(fileId).writeContent(fileId, readOnly);
  }

  @Override
  public void writeContent(int fileId, ByteSequence bytes, boolean readOnly) {
    getShard(fileId).writeContent(fileId, bytes, readOnly);
  }

  @Override
  public int storeUnlinkedContent(byte[] bytes) {
    return getAnyShard().storeUnlinkedContent(bytes);
  }

  @NotNull
  @Override
  public DataOutputStream writeAttribute(int fileId, @NotNull FileAttribute att) {
    return getShard(fileId).writeAttribute(fileId, att);
  }

  @Override
  public void writeBytes(int fileId, ByteSequence bytes, boolean preferFixedSize) throws IOException {
    getShard(fileId).writeBytes(fileId, bytes, preferFixedSize);
  }

  @Override
  public void dispose() {
    for (IFSRecords records : getShards()) {
      records.dispose();
    }
  }

  @Override
  public void invalidateCaches() {
    for (IFSRecords records : getShards()) {
      records.invalidateCaches();
    }
  }
}