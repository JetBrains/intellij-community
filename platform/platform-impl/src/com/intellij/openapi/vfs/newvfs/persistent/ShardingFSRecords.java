package com.intellij.openapi.vfs.newvfs.persistent;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.intellij.cassandra.CassandraIndexTable;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.function.IntPredicate;

public class ShardingFSRecords implements IFSRecords {

  private final Function<Integer, IFSRecords> myFactory;

  private PagedFileStorage.StorageLockContext myContext;
  private PersistentStringEnumerator myNames;
  private FileNameCache myCache;
  private VfsDependentEnum<String> myAttrsList;

  private TIntObjectHashMap<FSRecordsShard> myShards = new TIntObjectHashMap<>();

  private TIntObjectHashMap<FSRecordsSource.MountPoint> myMounts;
  private Multimap<Integer, Integer> myMountsInverted = ArrayListMultimap.create();

  public ShardingFSRecords(Function<Integer, IFSRecords> shardFactory) {
    myFactory = shardFactory;
  }

  public void dumpToCassandra() {
    getShards().forEach(FSRecordsShard::dumpToCassandra);
  }

  private FSRecordsShard getShard(int shardId) {
    if (myConnectedShards.contains(shardId)) {
      return myShards.get(shardId);
    }

    synchronized (myConnectedShards) {
      FSRecordsShard shard = myShards.get(shardId);
      if (myConnectedShards.contains(shardId)) {
        return shard;
      }
      shard.connect(myContext, myNames, myCache, myAttrsList);
      myConnectedShards.add(shardId);
      return shard;
    }
  }

  private FSRecordsShard getShardForRecord(int recordId) {
    return getShard(FSRecordsShard.getShardId(recordId));
  }

  public Collection<FSRecordsShard> getShards() {
    return ContainerUtil.map2List(myShards.getValues(), Function.ID);
  }

  private FSRecordsShard getAnyShard() {
    return getShard(1);
  }

  @Override
  public void writeAttributesToRecord(int id, int parentId, @NotNull FileAttributes attributes, @NotNull String name) {
    getShardForRecord(id).writeAttributesToRecord(id, parentId, attributes, name);
  }

  private Set<Integer> myConnectedShards = ContainerUtil.newConcurrentSet();

  @Override
  public void connect(PagedFileStorage.StorageLockContext lockContext, PersistentStringEnumerator names, FileNameCache fileNameCache, VfsDependentEnum<String> attrsList) {
    myContext = lockContext;
    myNames = names;
    myCache = fileNameCache;
    myAttrsList = attrsList;
    myMounts = CassandraIndexTable.getInstance().getMountedChildren(0);
    myMounts.forEach(id -> {
      FSRecordsSource.MountPoint mp = myMounts.get(id);

      myMountsInverted.put(FSRecordsShard.addShardId(mp.fileId, mp.shardId), id);

      return true;
    });
    for(int shardId = 0; shardId <= 2254; ++shardId) { // TODO iterate shards properly
      IFSRecords delegate = myFactory.fun(shardId);
      FSRecordsShard shard = new FSRecordsShard(shardId, delegate);
      myShards.put(shardId, shard);
    }
  }

  @Override
  public void force() {
    getShards().forEach(IFSRecords::force);
  }

  @Override
  public boolean isDirty() {
    return getShards().stream().anyMatch(IFSRecords::isDirty);
  }

  @Override
  public long getTimestamp() {
    return getCreationTimestamp();
  }

  @Override
  public void requestRebuild(@NotNull Throwable e) throws RuntimeException, Error {

  }

  @Override
  public void requestRebuild(int fileId, @NotNull Throwable e) throws RuntimeException, Error {
    getShardForRecord(fileId).requestRebuild(fileId, e);
  }

  @Override
  public int createChildRecord(int parentId) {
    return getShardForRecord(parentId).createChildRecord(parentId);
  }

  @Override
  public void deleteRecordRecursively(int id) {
    getShardForRecord(id).deleteRecordRecursively(id);
  }

  @NotNull
  @Override
  public RootRecord[] listRoots() {
    return getShards().stream()
      .flatMap(shard -> Arrays.stream(shard.listRoots()))
      .toArray(RootRecord[]::new);
  }

  @Override
  public int findRootRecord(@NotNull String rootUrl) {
    return Arrays.stream(listRoots())
      .filter(record -> record.url.equals(rootUrl))
      .findFirst()
      .map(record -> record.id)
      .orElseGet(() -> getAnyShard().findRootRecord(rootUrl));
  }

  @Override
  public void deleteRootRecord(int id) {
    getShardForRecord(id).deleteRootRecord(id);
  }

  @NotNull
  @Override
  public int[] list(int id) {
    int[] children = getShardForRecord(id).list(id);
    Collection<Integer> shards = myMountsInverted.get(id);
    if (shards == null) {
      return children;
    } else {
      TIntArrayList ch = new TIntArrayList(children);
      for (Integer shard : shards) {
        ch.add(FSRecordsShard.addShardId(2, shard)); // TODO: ugly hack!
      }
      return ch.toNativeArray();
    }
  }

  @NotNull
  @Override
  public NameId[] listAll(int parentId) {
    int[] ids = list(parentId);
    NameId[] result = new NameId[ids.length];
    for (int i = 0; i < ids.length; i++) {
      int id = ids[i];
      result[i] = new NameId(id, getNameId(id), getName(id));
    }
    return result;
  }

  @Override
  public boolean wereChildrenAccessed(int id) {
    return getShardForRecord(id).wereChildrenAccessed(id);
  }

  @Override
  public void updateList(int id, @NotNull int[] childIds) {
    Collection<Integer> shards = myMountsInverted.get(id);
    int[] filtered = ContainerUtil.filter(childIds, value -> !(FSRecordsShard.removeShardId(value) == 2 && shards.contains(FSRecordsShard.getShardId(value))));
    getShardForRecord(id).updateList(id, filtered);
  }

  @Override
  public long getCreationTimestamp() {
    return getShard(1).getCreationTimestamp();
  }

  @Override
  public int getLocalModCount() {
    return getShard(1).getLocalModCount();
  }

  @Override
  public int getModCount() {
    return getShard(1).getModCount();
  }




  @NotNull
  @Override
  public TIntArrayList getParents(int id, @NotNull IntPredicate cached) {
    FSRecordsShard shard = getShardForRecord(id);
    TIntArrayList parents = shard.getParents(id, cached);
    FSRecordsSource.MountPoint mp = myMounts.get(shard.getShardId());
    if (mp != null) {
      parents.add(getParents(FSRecordsShard.addShardId(mp.fileId, mp.shardId), cached).toNativeArray());
    }
    return parents;
  }

  @Override
  public void setParent(int id, int parentId) {
    getShardForRecord(id).setParent(id, parentId);
  }

  @Override
  public int getParent(int id) {
    int parent = getShardForRecord(id).getParent(id);
    if (parent == 0) {
      FSRecordsSource.MountPoint mount = myMounts.get(FSRecordsShard.getShardId(id));
      if (mount != null) {
        return FSRecordsShard.addShardId(mount.fileId, mount.shardId);
      } else {
        return 0;
      }
    } else {
      return parent;
    }
  }

  @Override
  public int getNameId(int id) {
    return getShardForRecord(id).getNameId(id);
  }

  @Override
  public int getNameId(String name) {
    try {
      return myNames.enumerate(name);
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    return -1;
  }

  @Override
  public String getName(int id) {
    return getShardForRecord(id).getName(id);
  }

  @NotNull
  @Override
  public CharSequence getNameSequence(int id) {
    return getShardForRecord(id).getNameSequence(id);
  }

  @Override
  public void setName(int id, @NotNull String name) {
    getShardForRecord(id).setName(id, name);
  }

  @Override
  public int getFlags(int id) {
    return getShardForRecord(id).getFlags(id);
  }

  @Override
  public void setFlags(int id, int flags, boolean markAsChange) {
    getShardForRecord(id).setFlags(id, flags, markAsChange);
  }

  @Override
  public long getLength(int id) {
    return getShardForRecord(id).getLength(id);
  }

  @Override
  public void setLength(int id, long len) {
    getShardForRecord(id).setLength(id, len);
  }

  @Override
  public long getTimestamp(int id) {
    return getShardForRecord(id).getTimestamp(id);
  }

  @Override
  public void setTimestamp(int id, long value) {
    getShardForRecord(id).setTimestamp(id, value);
  }

  @Override
  public int getModCount(int id) {
    return getShardForRecord(id).getModCount(id);
  }

  @Nullable
  @Override
  public DataInputStream readContent(int fileId) {
    return getShardForRecord(fileId).readContent(fileId);
  }

  @Nullable
  @Override
  public DataInputStream readContentById(int contentId) {
    return getShardForRecord(contentId).readContentById(contentId);
  }

  @Nullable
  @Override
  public DataInputStream readAttribute(int fileId, FileAttribute att) {
    return getShardForRecord(fileId).readAttribute(fileId, att);
  }

  @Override
  public int acquireFileContent(int fileId) {
    return getShardForRecord(fileId).acquireFileContent(fileId);
  }

  @Override
  public void releaseContent(int contentId) {
    getShardForRecord(contentId).releaseContent(contentId);
  }

  @Override
  public int getContentId(int fileId) {
    return getShardForRecord(fileId).getContentId(fileId);
  }

  @NotNull
  @Override
  public DataOutputStream writeContent(int fileId, boolean readOnly) {
    return getShardForRecord(fileId).writeContent(fileId, readOnly);
  }

  @Override
  public void writeContent(int fileId, ByteSequence bytes, boolean readOnly) {
    getShardForRecord(fileId).writeContent(fileId, bytes, readOnly);
  }

  @Override
  public int storeUnlinkedContent(byte[] bytes) {
    return getAnyShard().storeUnlinkedContent(bytes);
  }

  @NotNull
  @Override
  public DataOutputStream writeAttribute(int fileId, @NotNull FileAttribute att) {
    return getShardForRecord(fileId).writeAttribute(fileId, att);
  }

  @Override
  public void writeBytes(int fileId, ByteSequence bytes, boolean preferFixedSize) throws IOException {
    getShardForRecord(fileId).writeBytes(fileId, bytes, preferFixedSize);
  }

  @Override
  public void dispose() {
    getShards().forEach(IFSRecords::dispose);
  }

  @Override
  public void invalidateCaches() {
    getShards().forEach(IFSRecords::invalidateCaches);
  }
}