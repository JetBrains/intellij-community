package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.cassandra.CassandraIndexTable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.impl.FileNameCache;
import com.intellij.util.BitUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.*;
import com.twelvemonkeys.io.FileUtil;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.IntPredicate;

public class LazyFSRecords implements IFSRecords {

  private static int DELETED = -2;

  private final File myFile;
  private final IFSRecords mySink;
  private final FSRecordsSource mySource;
  private final Set<Pair<Integer, FileAttribute>> myDirtyAttrs = ContainerUtil.newConcurrentSet();
  private PersistentHashMap<Integer, Integer> myPublicToSink;
  private PersistentHashMap<Integer, Integer> mySinkToPublic;

  private TIntObjectHashMap<TIntIntHashMap> myTrees = new TIntObjectHashMap<>(); // shard -> tree

  private VfsDependentEnum<String> myAttrsList;

  public LazyFSRecords(File baseFile, IFSRecords sink, FSRecordsSource source) {
    myFile = baseFile;
    mySink = sink;
    mySource = source;
  }

  public void dumpToCassandra(int shardId) {
    CassandraIndexTable.getInstance().bulkInsertAttrs(
      shardId,
      myDirtyAttrs.stream().map(entry -> {
        try {
          return new CassandraIndexTable.AttrInfo(entry.first,
                                                  entry.second.getId(),
                                                  ByteBuffer.wrap(FileUtil.read(readAttribute(entry.first, entry.second))));
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }));
    myDirtyAttrs.clear();
  }

  @Override
  public synchronized void writeAttributesToRecord(int id, int parentId, @NotNull FileAttributes attributes, @NotNull String name) {
    ensureLoaded(id);
    mySink.writeAttributesToRecord(toSinkIdAsserting(id), toSinkIdAsserting(parentId), attributes, name);
  }

  int resolveShard(int id) {
    final int[] shardIdRef = new int[]{-1};
    myTrees.forEachEntry((shardId, shard) -> {
      if (shard.containsKey(id)) {
        shardIdRef[0] = shardId;
        return false;
      }
      return true;
    });
    return shardIdRef[0];
  }

  private void getAncestors(TIntIntHashMap tree, int id, IntPredicate consumer) {
    int pid = id;
    while (pid != 0) {
      if (consumer.test(pid)) {
        pid = tree.get(pid);
      } else {
        break;
      }
    }
  }

  boolean isLocal(int id) {
    return id % 2 == 1;
  }

  boolean isRoot(int id) {
    return myRootIdToUrl.containsKey(id);
  }

  void addRecord(FSRecordsSource.RecordInfo record, int parentId) {
    int newRecord = mySink.createChildRecord(-1);
    mySink.setName(newRecord, record.name);
    mySink.setTimestamp(newRecord, record.timestamp);
    mySink.setLength(newRecord, record.length);
    if (record.contentHash != null) {
      DataOutputStream stream = mySink.writeAttribute(newRecord, contentHashAttr);
      try {
        stream.write(record.contentHash.array(), record.contentHash.position(), record.contentHash.limit() - record.contentHash.position());
        stream.close();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    mySink.setFlags(newRecord, record.flags, false);
    mySink.setParent(newRecord, toSinkIdAsserting(parentId));
    addToMapping(record.id, newRecord);
  }

  private void ensureLoaded(int id) {
    if (toSinkId(id) != -1) {
      return;
    }

    int shardId = resolveShard(id);
    if (shardId == -1) {
      shardId = mySource.getShardId(id);
      TIntIntHashMap tree = mySource.getTree(shardId);
      myTrees.put(shardId, tree);
    }
    TIntIntHashMap tree = myTrees.get(shardId);
    final List<FSRecordsSource.RecordId> recordsToLoad = new ArrayList<>();
    int finalShardId = shardId;
    getAncestors(tree, id, parentId -> {
      if (toSinkId(parentId) != -1) {
        return false;
      } else {
        recordsToLoad.add(new FSRecordsSource.RecordId(parentId, finalShardId));
        return true;
      }
    });
    List<FSRecordsSource.RecordInfo> infos = mySource.loadRecords(recordsToLoad);
    for (int i = infos.size() - 1; i >= 0; i--) {
      FSRecordsSource.RecordInfo record = infos.get(i);
      int parentId = tree.get(record.id);
      if (parentId == 0 && !isRoot(record.id)) {
        ensureLoaded(record.id);
      } else {
        addRecord(record, parentId);
      }
    }
  }

  private final static FileAttribute contentHashAttr = new FileAttribute("lazyfsrecords.contenthash");

  private void ensureContentLoaded(int publicId) {
    if (isLocal(publicId)) return;
    ensureLoaded(publicId);

    int sinkId = toSinkId(publicId);
    int flags = mySink.getFlags(sinkId);
    boolean isDir = BitUtil.isSet(flags, PersistentFS.IS_DIRECTORY_FLAG);
    boolean fixedSize = BitUtil.isSet(flags, PersistentFS.IS_READ_ONLY);
    if (!isDir) {
      int cId = mySink.getContentId(sinkId);
      if (cId == 0) {
        DataInputStream contentHashStream = mySink.readAttribute(sinkId, contentHashAttr);
        if (contentHashStream == null) {
          return;
        }
        byte[] contentHash;
        try {
          contentHash = FileUtil.read(contentHashStream);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        ByteBuffer c = mySource.getContent(ByteBuffer.wrap(contentHash));
        if (c == null) {
          return;
        }
        DataOutputStream stream = mySink.writeContent(sinkId, fixedSize);
        try {
          stream.write(c.array(), c.position(), c.limit() - c.position());
          stream.close();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  private void ensureAttributeLoaded(int id, FileAttribute att) {
    if (isLocal(id)) return;
    ensureLoaded(id);
    int sinkId = toSinkId(id);
    if (sinkId == DELETED) return;
    DataInputStream is = mySink.readAttribute(sinkId, att);
    if (is != null) {
      return;
    }
    try {
      DataInputStream absentAttrsStream = mySink.readAttribute(sinkId, ourAbsentAttrsAttr);
      TIntArrayList absentIds = new TIntArrayList();
      int attrId = myAttrsList.getId(att.getId());
      if (absentAttrsStream != null) {
        int count = DataInputOutputUtil.readINT(absentAttrsStream);
        for (int i = 0; i < count; i++) {
          int absentId = DataInputOutputUtil.readINT(absentAttrsStream);
          if (absentId == attrId) {
            return;
          }
          absentIds.add(absentId);
        }
      }
      int shardId = resolveShard(id);
      ByteBuffer value = mySource.readAttr(new FSRecordsSource.RecordId(id, shardId), att.getId(), att.getVersion());
      if (value == null) {
        absentIds.add(attrId);
        DataOutputStream os = mySink.writeAttribute(sinkId, ourAbsentAttrsAttr);
        DataInputOutputUtil.writeINT(os, absentIds.size());
        absentIds.forEach(absentId -> {
          try {
            DataInputOutputUtil.writeINT(os, absentId);
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
          return true;
        });
      }
      else {
        DataOutputStream os = mySink.writeAttribute(sinkId, att);
        os.write(value.array(), value.position(), value.limit() - value.position());
        os.close();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Map<String, Integer> myUrlToRootId = new HashMap<>();
  private Map<Integer, String> myRootIdToUrl = new HashMap<>();

  @Override
  public void connect(PagedFileStorage.StorageLockContext lockContext, PersistentStringEnumerator names, FileNameCache fileNameCache, VfsDependentEnum<String> attrsList) {
    myAttrsList = attrsList;
    try {
      myPublicToSink = new PersistentHashMap<>(new File(myFile, "publicIds"), new IntInlineKeyDescriptor(), new IntInlineKeyDescriptor());
      mySinkToPublic = new PersistentHashMap<>(new File(myFile, "sinkIds"), new IntInlineKeyDescriptor(), new IntInlineKeyDescriptor());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    mySink.connect(lockContext, names, fileNameCache, myAttrsList);
    for(RootRecord rr : mySource.getRoots()) {
      myUrlToRootId.put(rr.url, rr.id);
      myRootIdToUrl.put(rr.id, rr.url);
    }
  }

  int toSinkId(int publicId) {
    if (publicId == 0) return 0;
    try {
      Integer map = myPublicToSink.get(publicId);
      return map == null ? -1 : map;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  int toPublicId(int sinkId) {
    if (sinkId == 0) return 0;
    try {
      Integer publicId = mySinkToPublic.get(sinkId);
      assert publicId != null;
      return publicId;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  int toSinkIdAsserting(int publicId) {
    int x = toSinkId(publicId);
    if (x < 0){
      throw new AssertionError("x = " + x + " id: " + publicId);
    }
    return x;
  }

  @Override
  public void force() {
    mySink.force();
  }

  @Override
  public boolean isDirty() {
    return mySink.isDirty();
  }

  @Override
  public long getTimestamp() {
    return mySink.getTimestamp();
  }

  @Override
  public void requestRebuild(@NotNull Throwable e) throws RuntimeException, Error {
    mySink.requestRebuild(e);
  }

  @Override
  public void requestRebuild(int fileId, @NotNull Throwable e) throws RuntimeException, Error {
    mySink.requestRebuild(toSinkId(fileId), e);
  }

  @Override
  public long getCreationTimestamp() {
    return mySink.getCreationTimestamp();
  }

  void addToMapping(int publicId, int sinkId) {
    try {
      myPublicToSink.put(publicId, sinkId);
      mySinkToPublic.put(sinkId, publicId);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  synchronized public int createChildRecord(int parentId) {
    ensureLoaded(parentId);
    int recordId = mySink.createChildRecord(toSinkIdAsserting(parentId));
    int publicId = recordId * 2 + 1;
    updateLocalList(recordId, new int[0]);
    addToMapping(publicId, recordId);
    return publicId;
  }

  private static FileAttribute ourPublicChildrenAttr = new FileAttribute("LazyFSRecords.children");
  private static FileAttribute ourAbsentAttrsAttr = new FileAttribute("LazyFSRecords.absentAttrs");

  int[] listOffline(int sinkId) {
    DataInputStream stream = mySink.readAttribute(sinkId, ourPublicChildrenAttr);
    if (stream == null) {
      return null;
    }
    try {
      int count = DataInputOutputUtil.readINT(stream);
      int[] res = new int[count];
      int prev = 0;
      for (int i = 0; i < count; ++i) {
        res[i] = DataInputOutputUtil.readINT(stream) + prev;
        prev = res[i];
      }
      return res;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  void markDeletedRecursively(int publicId) {
    int[] children = listOffline(toSinkIdAsserting(publicId));
    addToMapping(publicId, DELETED);
    if (children != null) {
      for (int c : children) {
        markDeletedRecursively(c);
      }
    }
  }

  @Override
  synchronized public void deleteRecordRecursively(int id) {
    ensureLoaded(id);
    int sinkId = toSinkId(id);
    if (sinkId == DELETED) return;
    int parentSinkId = mySink.getParent(sinkId);
    if (parentSinkId != 0) {
      int[] siblingsPublicIds = listOffline(parentSinkId);
      int[] updatedSiblings = ContainerUtil.filter(siblingsPublicIds, sibling -> sibling != id);
      updateLocalList(parentSinkId, updatedSiblings);
    }
    markDeletedRecursively(id);

    //mySink.deleteRecordRecursively(sinkId);
  }

  @NotNull
  @Override
  synchronized public RootRecord[] listRoots() {
    return myUrlToRootId.entrySet().stream()
      .map(entry -> new RootRecord(entry.getValue(), entry.getKey()))
      .toArray(RootRecord[]::new);
  }

  @Override
  synchronized public int findRootRecord(@NotNull String rootUrl) {
    Integer publicRootId = myUrlToRootId.get(rootUrl);
    int sinkId = mySink.findRootRecord(rootUrl);
    int publicId = publicRootId == null ? (sinkId * 2 + 1) : publicRootId;
    if (publicRootId == null) {
      updateLocalList(sinkId, new int[0]);
    }
    addToMapping(publicId, sinkId);
    return publicId;
  }

  @Override
  synchronized public void deleteRootRecord(int id) {
    int sinkId = toSinkId(id);
    if (sinkId == DELETED) {
      throw new RuntimeException("trying to delete already deleted record");
    }
    if (sinkId > 0) {
      mySink.deleteRootRecord(sinkId);
    }
    addToMapping(id, DELETED);
  }

  private void updateLocalList(int sinkId, int [] childrenIds) {
    DataOutputStream stream = mySink.writeAttribute(sinkId, ourPublicChildrenAttr);
    int prev = 0;
    try {
      DataInputOutputUtil.writeINT(stream, childrenIds.length);
      for (int childrenId : childrenIds) {
        DataInputOutputUtil.writeINT(stream, childrenId - prev);
        prev = childrenId;
      }
      stream.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  @Override
  synchronized public int[] list(int id) {
    ensureLoaded(id);
    int[] offline = listOffline(toSinkIdAsserting(id));
    if (offline != null){
      return offline;
    }
    int shardId = resolveShard(id);
    TIntIntHashMap tree = myTrees.get(shardId);
    List<FSRecordsSource.RecordId> recordsToLoad = new ArrayList<>();
    tree.forEachEntry((c, p) -> {
      if (p == id) {
        recordsToLoad.add(new FSRecordsSource.RecordId(c, shardId));
      }
      return true;
    });
    List<FSRecordsSource.RecordId> forChildren = mySource.getMountedChildren(new FSRecordsSource.RecordId(id, shardId));
    recordsToLoad.addAll(forChildren);
    List<FSRecordsSource.RecordInfo> records = mySource.loadRecords(recordsToLoad);
    for (FSRecordsSource.RecordInfo record : records) {
      addRecord(record, id);
    }

    int[] res = new int[records.size()];
    for (int i = 0; i < recordsToLoad.size(); i++) {
      res[i] = recordsToLoad.get(i).fileId;
    }
    updateLocalList(toSinkId(id), res);
    return res;
  }

  @NotNull
  @Override
  synchronized public NameId[] listAll(int parentId) {
    int[] children = list(parentId);
    NameId[] result = new NameId[children.length];
    for (int i = 0; i < children.length; i++) {
      result[i] = new NameId(children[i], getNameId(children[i]), getName(children[i]));
    }
    return result;
  }

  @Override
  synchronized public boolean wereChildrenAccessed(int id) {
    int sinkId = toSinkId(id);
    return sinkId > 0 && mySink.wereChildrenAccessed(sinkId);
  }

  @Override
  synchronized public void updateList(int id, @NotNull int[] childIds) {
    for (int childId : childIds) {
      if (childId == 2) {
        System.out.println("problem");
      }
    }
    int sinkId = toSinkIdAsserting(id);
    updateLocalList(sinkId, childIds);
    int[] sinkChildren = new int[childIds.length];
    for (int i = 0; i < childIds.length; i++) {
      sinkChildren[i] = toSinkIdAsserting(childIds[i]);
    }
    mySink.updateList(sinkId, sinkChildren);
  }

  @Override
  synchronized public int getLocalModCount() {
    return mySink.getLocalModCount();
  }

  @Override
  synchronized public int getModCount() {
    return mySink.getModCount();
  }

  @NotNull
  @Override
  synchronized public TIntArrayList getParents(int id, @NotNull IntPredicate cached) {
    ensureLoaded(id);
    TIntArrayList parents = mySink.getParents(toSinkId(id), recordId -> cached.test(toPublicId(recordId)));
    parents.transformValues(sinkId -> toPublicId(sinkId));
    return parents;
  }

  @Override
  synchronized public void setParent(int id, int parentId) {
    ensureLoaded(id);
    mySink.setParent(toSinkIdAsserting(id), toSinkIdAsserting(parentId));
  }

  @Override
  synchronized public int getParent(int id) {
    ensureLoaded(id);
    return toPublicId(mySink.getParent(toSinkId(id)));
  }

  @Override
  synchronized public int getNameId(int id) {
    ensureLoaded(id);
    int nameId = mySink.getNameId(toSinkId(id));
    if (nameId == -1) {
      throw new AssertionError("nameId = -1 id: " + id);
    }
    return nameId;
  }

  @Override
  synchronized public int getNameId(String name) {
    int nameId = mySink.getNameId(name);
    if (nameId == -1) {
      throw new AssertionError("nameId = -1");
    }
    return nameId;
  }

  @Override
  synchronized public String getName(int recordId) {
    ensureLoaded(recordId);
    return mySink.getName(toSinkId(recordId));
  }

  @NotNull
  @Override
  synchronized public CharSequence getNameSequence(int id) {
    ensureLoaded(id);
    return mySink.getNameSequence(toSinkIdAsserting(id));
  }

  @Override
  synchronized public void setName(int id, @NotNull String name) {
    ensureLoaded(id);
    mySink.setName(toSinkId(id), name);
  }

  @Override
  synchronized public int getFlags(int id) {
    ensureLoaded(id);
    return mySink.getFlags(toSinkId(id));
  }

  @Override
  synchronized public void setFlags(int id, int flags, boolean markAsChange) {
    ensureLoaded(id);
    mySink.setFlags(toSinkId(id), flags, markAsChange);
  }

  @Override
  synchronized public long getLength(int id) {
    ensureLoaded(id);
    return mySink.getLength(toSinkId(id));
  }

  @Override
  synchronized public void setLength(int id, long len) {
    ensureLoaded(id);
    mySink.setLength(toSinkId(id), len);
  }

  @Override
  synchronized public long getTimestamp(int id) {
    ensureLoaded(id);
    return mySink.getTimestamp(toSinkId(id));
  }

  @Override
  synchronized public void setTimestamp(int id, long value) {
    ensureLoaded(id);
    mySink.setTimestamp(toSinkId(id), value);
  }

  @Override
  synchronized public int getModCount(int id) {
    ensureLoaded(id);
    return mySink.getModCount(toSinkId(id));
  }

  @Nullable
  @Override
  synchronized public DataInputStream readContent(int fileId) {
    ensureContentLoaded(fileId);
    int sinkId = toSinkId(fileId);
    if (sinkId == DELETED) return null;
    return mySink.readContent(sinkId);
  }

  @Nullable
  @Override
  synchronized public DataInputStream readContentById(int contentId) {
    return mySink.readContentById(contentId);
  }

  @Nullable
  @Override
  synchronized public DataInputStream readAttribute(int fileId, FileAttribute att) {
    ensureAttributeLoaded(fileId, att);
    int sinkid = toSinkId(fileId);
    if (sinkid == DELETED) return null;
    return mySink.readAttribute(sinkid, att);
  }

  @Override
  synchronized public int acquireFileContent(int fileId) {
    ensureContentLoaded(fileId);
    return mySink.acquireFileContent(toSinkIdAsserting(fileId));
  }

  @Override
  synchronized public void releaseContent(int contentId) {
    mySink.releaseContent(contentId);
  }

  @Override
  synchronized public int getContentId(int fileId) {
    ensureContentLoaded(fileId);
    return mySink.getContentId(toSinkIdAsserting(fileId));
  }

  @NotNull
  @Override
  synchronized public DataOutputStream writeContent(int fileId, boolean fixedSize) {
    ensureLoaded(fileId);
    return mySink.writeContent(toSinkIdAsserting(fileId), fixedSize);
  }

  @Override
  synchronized public void writeContent(int fileId, ByteSequence bytes, boolean fixedSize) {
    ensureLoaded(fileId);
    mySink.writeContent(toSinkIdAsserting(fileId), bytes, fixedSize);
  }

  @Override
  synchronized public int storeUnlinkedContent(byte[] bytes) {
    return mySink.storeUnlinkedContent(bytes);
  }

  @NotNull
  @Override
  synchronized public DataOutputStream writeAttribute(int fileId, @NotNull FileAttribute att) {
    ensureLoaded(fileId);
    myDirtyAttrs.add(Pair.create(fileId, att));
    return mySink.writeAttribute(toSinkIdAsserting(fileId), att);
  }

  @Override
  synchronized public void writeBytes(int fileId, ByteSequence bytes, boolean preferFixedSize) throws IOException {
    ensureLoaded(fileId);
    mySink.writeBytes(toSinkIdAsserting(fileId), bytes, preferFixedSize);
  }

  @Override
  public void dispose() {
    mySink.dispose();
    try {
      myPublicToSink.close();
      mySinkToPublic.close();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void invalidateCaches() {
    mySink.invalidateCaches();
  }
}