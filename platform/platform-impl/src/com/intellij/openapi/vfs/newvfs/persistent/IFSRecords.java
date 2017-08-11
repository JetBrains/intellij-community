/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.impl.FileNameCache;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.PersistentStringEnumerator;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.function.IntPredicate;

public interface IFSRecords {
  void writeAttributesToRecord(int id, int parentId, @NotNull FileAttributes attributes, @NotNull String name);

  void connect(PagedFileStorage.StorageLockContext lockContext, PersistentStringEnumerator names, FileNameCache fileNameCache, VfsDependentEnum<String> attrsList);

  void force();

  boolean isDirty();

  long getTimestamp();

  void handleError(@NotNull Throwable e) throws RuntimeException, Error;

  void handleError(int fileId, @NotNull Throwable e) throws RuntimeException, Error;

  long getCreationTimestamp();

  // todo: Address  / capacity store in records table, size store with payload
  int createChildRecord(int parentId);

  void deleteRecordRecursively(int id);

  @NotNull
  int[] listRoots();

  int findRootRecord(@NotNull String rootUrl);

  void deleteRootRecord(int id);

  @NotNull
  int[] list(int id);

  @NotNull
  NameId[] listAll(int parentId);

  boolean wereChildrenAccessed(int id);

  void updateList(int id, @NotNull int[] childIds);

  int getLocalModCount();

  int getModCount();

  // returns id, parent(id), parent(parent(id)), ...  (already cached id or rootId)
  @NotNull
  TIntArrayList getParents(int id, @NotNull IntPredicate cached);

  void setParent(int id, int parentId);

  int getParent(int id);

  int getNameId(int id);

  int getNameId(String name);

  String getName(int id);

  @NotNull
  CharSequence getNameSequence(int id);

  void setName(int id, @NotNull String name);

  int getFlags(int id);

  void setFlags(int id, int flags, boolean markAsChange);

  long getLength(int id);

  void setLength(int id, long len);

  long getTimestamp(int id);

  void setTimestamp(int id, long value);

  int getModCount(int id);

  @Nullable
  DataInputStream readContent(int fileId);

  @Nullable
  DataInputStream readContentById(int contentId);

  @Nullable
  DataInputStream readAttribute(int fileId, FileAttribute att);

  int acquireFileContent(int fileId);

  void releaseContent(int contentId);

  int getContentId(int fileId);

  @NotNull
  DataOutputStream writeContent(int fileId, boolean fixedSize);

  void writeContent(int fileId, ByteSequence bytes, boolean fixedSize);

  int storeUnlinkedContent(byte[] bytes);

  @NotNull
  DataOutputStream writeAttribute(int fileId, @NotNull FileAttribute att);

  void writeBytes(int fileId, ByteSequence bytes, boolean preferFixedSize) throws IOException;

  void dispose();

  void invalidateCaches();

  class NameId {
    @NotNull
    public static final NameId[] EMPTY_ARRAY = new NameId[0];
    public final int id;
    public final CharSequence name;
    public final int nameId;

    public NameId(int id, int nameId, @NotNull CharSequence name) {
      this.id = id;
      this.nameId = nameId;
      this.name = name;
    }

    public NameId withId(int id) {
      return new NameId(id, nameId, name);
    }

    @Override
    public String toString() {
      return name + " (" + id + ")";
    }
  }
}
