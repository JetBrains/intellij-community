package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.cassandra.CassandraIndexTable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.impl.FileNameCache;
import com.intellij.util.BitUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.StorageException;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

public class LazyFSRecords implements IFSRecords {

  private static int DELETED = -2;

  private final File myFile;
  private final IFSRecords mySink;
  private final FSRecordsSource mySource;
  private final Set<Pair<Integer, FileAttribute>> myDirtyAttrs = ContainerUtil.newConcurrentSet();
  private PersistentHashMap<Integer, Integer> myPublicToSink;
  private PersistentHashMap<Integer, Integer> mySinkToPublic;
  private TIntIntHashMap myTreeStructure = new TIntIntHashMap();
  private VfsDependentEnum<String> myAttrsList;
  private Map<String, Integer> myRoots;
  private int mySourceMaxId;

  public LazyFSRecords(File baseFile, IFSRecords sink, FSRecordsSource source) {
    myFile = baseFile;
    mySink = sink;
    mySource = source;
    FSRecordsSource.SourceInfo init = mySource.connect();
    myRoots = init.roots;
    mySourceMaxId = init.maxId;
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
  public void writeAttributesToRecord(int id, int parentId, @NotNull FileAttributes attributes, @NotNull String name) {
    ensureLoaded(new int[]{id, parentId});
    mySink.writeAttributesToRecord(toSinkIdAsserting(id), toSinkIdAsserting(parentId), attributes, name);
  }

  private boolean ensureLoaded(int[] ids){
    TIntArrayList idsToLoad = new TIntArrayList();
    for (int id : ids) {
      if (id > mySourceMaxId) continue;
      if (toSinkId(id) == -1) {
        idsToLoad.add(id);
      }
    }
    TIntArrayList recordsToLoad = new TIntArrayList();
    if (idsToLoad.isEmpty()) return false;
    idsToLoad.forEach(value -> {
      Integer parent = myTreeStructure.containsKey(value) ? myTreeStructure.get(value) : null;
      while (parent != null) {
        if (toSinkId(parent) == -1) {
          recordsToLoad.add(parent);
        }
        parent = myTreeStructure.containsKey(parent) ? myTreeStructure.get(parent) : null;
      }
      return true;
    });
    recordsToLoad.reverse();
    recordsToLoad.add(idsToLoad.toNativeArray());
    List<FSRecordsSource.RecordInfo> infos = mySource.loadRecords(recordsToLoad);
    TIntObjectHashMap<FSRecordsSource.RecordInfo> loaded = new TIntObjectHashMap<>();
    for (FSRecordsSource.RecordInfo info : infos) {
      loaded.put(info.id, info);
    }
    recordsToLoad.forEach(id -> {
      FSRecordsSource.RecordInfo record = loaded.get(id);
      if (record == null) {
        throw new AssertionError("not loaded: " + id + " ids: " + Arrays.toString(ids));
      }
      synchronized (mySink) {
        if (toSinkId(record.id) == -1) {
          int newRecord = mySink.createChildRecord(-1);
          mySink.setName(newRecord, record.name);
          mySink.setTimestamp(newRecord, record.timestamp);
          mySink.setLength(newRecord, record.length);
          mySink.setFlags(newRecord, record.flags, false);
          Integer parentId = myTreeStructure.containsKey(record.id) ? myTreeStructure.get(record.id) : null;
          if (parentId == null) {
            mySink.setParent(newRecord, 0);
          }
          else {
            int parentSinkId = toSinkIdAsserting(parentId);
            mySink.setParent(newRecord, parentSinkId);
          }
          addToMapping(record.id, newRecord);
        }
      }
      return true;
    });
    for (int i : ids) {
      toSinkIdAsserting(i);
    }
    return true;
  }


  private void ensureContentLoaded(int publicId) {
    if (publicId > mySourceMaxId) return;
    ensureLoaded(new int[]{publicId});
    int sinkId = toSinkId(publicId);
    int flags = mySink.getFlags(sinkId);
    boolean isDir = BitUtil.isSet(flags, PersistentFS.IS_DIRECTORY_FLAG);
    boolean fixedSize = BitUtil.isSet(flags, PersistentFS.IS_READ_ONLY);
    if (!isDir) {
      int cId = mySink.getContentId(sinkId);
      if (cId == 0) {
        ByteBuffer c = mySource.getContent(publicId);
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
    if (id > mySourceMaxId) {
      return;
    }
    ensureLoaded(new int[]{id});
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

      ByteBuffer value = mySource.readAttr(id, att.getId(), att.getVersion());
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

  @Override
  public void connect(PagedFileStorage.StorageLockContext lockContext, PersistentStringEnumerator names, FileNameCache fileNameCache, VfsDependentEnum<String> attrsList) {
    myAttrsList = attrsList;
    try {
      myPublicToSink = new PersistentHashMap<>(new File(myFile, "publicIds"), new IntInlineKeyDescriptor(), new IntInlineKeyDescriptor());
      mySinkToPublic = new PersistentHashMap<>(new File(myFile, "sinkIds"), new IntInlineKeyDescriptor(), new IntInlineKeyDescriptor());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    List<Integer> tree = mySource.getTree();
    for (int i = 0; i < tree.size(); i+= 2) {
      Integer child = tree.get(i);
      Integer parent = tree.get(i+1);
      myTreeStructure.put(child, parent);
    }
    mySink.connect(lockContext, names, fileNameCache, myAttrsList);
    System.out.println("CONNECTED " + ((FSRecordsSource.CassandraFSRecordsSource)mySource).getShard() + myRoots);
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
      throw new AssertionError("x = " + x + " id: " + publicId + "maxId: " + mySourceMaxId);
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
    ensureLoaded(new int [] { parentId });
    int recordId = mySink.createChildRecord(toSinkIdAsserting(parentId));
    int publicId = recordId + mySourceMaxId;
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
    ensureLoaded(new int[]{id});
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
    return myRoots.entrySet().stream().map(entry -> new RootRecord(entry.getValue(), entry.getKey())).toArray(RootRecord[]::new);
  }

  @Override
  synchronized public int findRootRecord(@NotNull String rootUrl) {
    Integer publicRootId = myRoots.get(rootUrl);
    int sinkId = mySink.findRootRecord(rootUrl);
    int publicId = publicRootId == null ? mySourceMaxId + sinkId : publicRootId;
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
    ensureLoaded(new int[]{id});
    int[] offline = listOffline(toSinkIdAsserting(id));
    if (offline != null){
      return offline;
    }
    if (id > mySourceMaxId) {
      throw new IllegalStateException();
    }

    TIntArrayList result = new TIntArrayList();
    myTreeStructure.forEachEntry((child, parent) -> {
      if (parent == id) {
        result.add(child);
      }
      return true;
    });

    int[] res = result.toNativeArray();
    updateLocalList(toSinkId(id), res);
    return res;
  }

  @NotNull
  @Override
  synchronized public NameId[] listAll(int parentId) {
    int[] children = list(parentId);
    ensureLoaded(children);
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
    ensureLoaded(new int[]{id});
    TIntArrayList parents = mySink.getParents(toSinkId(id), recordId -> cached.test(toPublicId(recordId)));
    parents.transformValues(sinkId -> toPublicId(sinkId));
    return parents;
  }

  @Override
  synchronized public void setParent(int id, int parentId) {
    ensureLoaded(new int[]{id, parentId});
    mySink.setParent(toSinkIdAsserting(id), toSinkIdAsserting(parentId));
  }

  @Override
  synchronized public int getParent(int id) {
    ensureLoaded(new int[]{id});
    return toPublicId(mySink.getParent(toSinkId(id)));
  }

  @Override
  synchronized public int getNameId(int id) {
    ensureLoaded(new int[]{id});
    return mySink.getNameId(toSinkId(id));
  }

  @Override
  synchronized public int getNameId(String name) {
    return mySink.getNameId(name);
  }

  @Override
  synchronized public String getName(int recordId) {
    ensureLoaded(new int[]{recordId});
    return mySink.getName(toSinkId(recordId));
  }

  @NotNull
  @Override
  synchronized public CharSequence getNameSequence(int id) {
    ensureLoaded(new int[] {id});
    return mySink.getNameSequence(toSinkIdAsserting(id));
  }

  @Override
  synchronized public void setName(int id, @NotNull String name) {
    ensureLoaded(new int[]{id});
    mySink.setName(toSinkId(id), name);
  }

  @Override
  synchronized public int getFlags(int id) {
    ensureLoaded(new int[] {id});
    return mySink.getFlags(toSinkId(id));
  }

  @Override
  synchronized public void setFlags(int id, int flags, boolean markAsChange) {
    ensureLoaded(new int[] {id});
    mySink.setFlags(toSinkId(id), flags, markAsChange);
  }

  @Override
  synchronized public long getLength(int id) {
    ensureLoaded(new int[] {id});
    return mySink.getLength(toSinkId(id));
  }

  @Override
  synchronized public void setLength(int id, long len) {
    ensureLoaded(new int[] {id});
    mySink.setLength(toSinkId(id), len);
  }

  @Override
  synchronized public long getTimestamp(int id) {
    ensureLoaded(new int[] {id});
    return mySink.getTimestamp(toSinkId(id));
  }

  @Override
  synchronized public void setTimestamp(int id, long value) {
    ensureLoaded(new int[] {id});
    mySink.setTimestamp(toSinkId(id), value);
  }

  @Override
  synchronized public int getModCount(int id) {
    ensureLoaded(new int[] {id});
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
    ensureLoaded(new int []{fileId});
    return mySink.writeContent(toSinkIdAsserting(fileId), fixedSize);
  }

  @Override
  synchronized public void writeContent(int fileId, ByteSequence bytes, boolean fixedSize) {
    ensureLoaded(new int[]{fileId});
    mySink.writeContent(toSinkIdAsserting(fileId), bytes, fixedSize);
  }

  @Override
  synchronized public int storeUnlinkedContent(byte[] bytes) {
    return mySink.storeUnlinkedContent(bytes);
  }

  @NotNull
  @Override
  synchronized public DataOutputStream writeAttribute(int fileId, @NotNull FileAttribute att) {
    ensureLoaded(new int[]{fileId});
    myDirtyAttrs.add(Pair.create(fileId, att));
    return mySink.writeAttribute(toSinkIdAsserting(fileId), att);
  }

  @Override
  synchronized public void writeBytes(int fileId, ByteSequence bytes, boolean preferFixedSize) throws IOException {
    ensureLoaded(new int[]{fileId});
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