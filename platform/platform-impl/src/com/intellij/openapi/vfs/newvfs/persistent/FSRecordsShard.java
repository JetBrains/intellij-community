package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.impl.FileNameCache;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.PersistentStringEnumerator;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.function.IntPredicate;

public class FSRecordsShard implements IFSRecords {

  private final int myShardId;
  private final IFSRecords myDelegate;

  public FSRecordsShard(int shardId, IFSRecords delegate) {
    myShardId = shardId;
    myDelegate = delegate;
  }

  private static int removeShardId(int id) {
    return id >> 8;
  }

  private int addShardId(int id) {
    return myShardId | (id << 8);
  }

  private int[] addShardId(int[] ids) {
    int[] res = new int[ids.length];
    for (int i = 0; i < ids.length; i++) {
      res[i] = addShardId(ids[i]);
    }
    return res;
  }

  private static int[] removeShardId(int[] ids) {
    int[] res = new int[ids.length];
    for (int i = 0; i < ids.length; i++) {
      res[i] = removeShardId(ids[i]);
    }
    return res;
  }

  @Override
  public void writeAttributesToRecord(int id, int parentId, @NotNull FileAttributes attributes, @NotNull String name) {
    myDelegate.writeAttributesToRecord(removeShardId(id), removeShardId(parentId), attributes, name);
  }

  @Override
  public void connect(PagedFileStorage.StorageLockContext lockContext, PersistentStringEnumerator names, FileNameCache fileNameCache, VfsDependentEnum<String> attrsList) {
    myDelegate.connect(lockContext, names, fileNameCache, attrsList);
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
  public void handleError(@NotNull Throwable e) throws RuntimeException, Error {
    myDelegate.handleError(e);
  }

  @Override
  public void handleError(int fileId, @NotNull Throwable e) throws RuntimeException, Error {
    myDelegate.handleError(removeShardId(fileId), e);
  }

  @Override
  public long getCreationTimestamp() {
    return myDelegate.getCreationTimestamp();
  }

  @Override
  public int createChildRecord(int parentId) {
    return addShardId(myDelegate.createChildRecord(removeShardId(parentId)));
  }

  @Override
  public void deleteRecordRecursively(int id) {
    myDelegate.deleteRecordRecursively(removeShardId(id));
  }

  @NotNull
  @Override
  public int[] listRoots() {
    return addShardId(myDelegate.listRoots());
  }

  @Override
  public int findRootRecord(@NotNull String rootUrl) {
    return addShardId(myDelegate.findRootRecord(rootUrl));
  }

  @Override
  public void deleteRootRecord(int id) {
    myDelegate.deleteRootRecord(removeShardId(id));
  }

  @NotNull
  @Override
  public int[] list(int id) {
    return addShardId(myDelegate.list(removeShardId(id)));
  }

  @NotNull
  @Override
  public NameId[] listAll(int parentId) {
    return ContainerUtil.map2Array(myDelegate.listAll(removeShardId(parentId)), NameId.class, nameId -> nameId.withId(addShardId(nameId.id)));
  }

  @Override
  public boolean wereChildrenAccessed(int id) {
    return myDelegate.wereChildrenAccessed(removeShardId(id));
  }

  @Override
  public void updateList(int id, @NotNull int[] childIds) {
    myDelegate.updateList(removeShardId(id), removeShardId(childIds));
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
    TIntArrayList parents = myDelegate.getParents(removeShardId(id), i -> cached.test(addShardId(i)));
    TIntArrayList res = new TIntArrayList(parents.size());
    for (int i = 0; i < parents.size(); ++i) {
      res.add(addShardId(parents.getQuick(i)));
    }
    return res;
  }

  @Override
  public void setParent(int id, int parentId) {
    myDelegate.setParent(removeShardId(id), removeShardId(parentId));
  }

  @Override
  public int getParent(int id) {
    return addShardId(myDelegate.getParent(removeShardId(id)));
  }

  @Override
  public int getNameId(int id) {
    return myDelegate.getNameId(removeShardId(id));
  }

  @Override
  public int getNameId(String name) {
    return myDelegate.getNameId(name);
  }

  @Override
  public String getName(int id) {
    return myDelegate.getName(removeShardId(id));
  }

  @NotNull
  @Override
  public CharSequence getNameSequence(int id) {
    return myDelegate.getNameSequence(removeShardId(id));
  }

  @Override
  public void setName(int id, @NotNull String name) {
    myDelegate.setName(removeShardId(id), name);
  }

  @Override
  public int getFlags(int id) {
    return myDelegate.getFlags(removeShardId(id));
  }

  @Override
  public void setFlags(int id, int flags, boolean markAsChange) {
    myDelegate.setFlags(removeShardId(id), flags, markAsChange);
  }

  @Override
  public long getLength(int id) {
    return myDelegate.getLength(removeShardId(id));
  }

  @Override
  public void setLength(int id, long len) {
    myDelegate.setLength(removeShardId(id), len);
  }

  @Override
  public long getTimestamp(int id) {
    return myDelegate.getTimestamp(removeShardId(id));
  }

  @Override
  public void setTimestamp(int id, long value) {
    myDelegate.setTimestamp(removeShardId(id), value);
  }

  @Override
  public int getModCount(int id) {
    return myDelegate.getModCount(removeShardId(id));
  }

  @Nullable
  @Override
  public DataInputStream readContent(int fileId) {
    return myDelegate.readContent(removeShardId(fileId));
  }

  @Nullable
  @Override
  public DataInputStream readContentById(int contentId) {
    return myDelegate.readContentById(removeShardId(contentId));
  }

  @Nullable
  @Override
  public DataInputStream readAttribute(int fileId, FileAttribute att) {
    return myDelegate.readAttribute(removeShardId(fileId), att);
  }

  @Override
  public int acquireFileContent(int fileId) {
    return addShardId(myDelegate.acquireFileContent(removeShardId(fileId)));
  }

  @Override
  public void releaseContent(int contentId) {
    myDelegate.releaseContent(removeShardId(contentId));
  }

  @Override
  public int getContentId(int fileId) {
    return addShardId(myDelegate.getContentId(removeShardId(fileId)));
  }

  @NotNull
  @Override
  public DataOutputStream writeContent(int fileId, boolean fixedSize) {
    return myDelegate.writeContent(removeShardId(fileId), fixedSize);
  }

  @Override
  public void writeContent(int fileId, ByteSequence bytes, boolean fixedSize) {
    myDelegate.writeContent(removeShardId(fileId), bytes, fixedSize);
  }

  @Override
  public int storeUnlinkedContent(byte[] bytes) {
    return addShardId(myDelegate.storeUnlinkedContent(bytes));
  }

  @NotNull
  @Override
  public DataOutputStream writeAttribute(int fileId, @NotNull FileAttribute att) {
    return myDelegate.writeAttribute(removeShardId(fileId), att);
  }

  @Override
  public void writeBytes(int fileId, ByteSequence bytes, boolean preferFixedSize) throws IOException {
    myDelegate.writeBytes(removeShardId(fileId), bytes, preferFixedSize);
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