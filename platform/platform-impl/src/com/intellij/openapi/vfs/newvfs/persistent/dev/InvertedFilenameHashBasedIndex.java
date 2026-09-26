// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.openapi.vfs.newvfs.persistent.DefaultInMemoryInvertedNameIndex;
import com.intellij.platform.util.io.storages.intmultimaps.Int2IntMultimap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Replacement of {@linkplain DefaultInMemoryInvertedNameIndex}, keeps (name.hash -> fileId*)
 * mapping in a specialized (int->int*) hashmap.
 *
 * This implementation is not compatible with {@link com.intellij.openapi.vfs.newvfs.persistent.InvertedNameIndex} interface,
 * it explores alternative approach to build fileName index, with String.hash instead of
 * nameIds
 */
@ApiStatus.Internal
public final class InvertedFilenameHashBasedIndex {
  // We want (names -> files) mapping, here we have names:Set<String> -> fileIds: IntPredicate
  //  Current implementation (InvertedNameIndex) uses nameId to build a map nameId->(fileId)*
  //  If we can't use (can't rely upon) nameId <-> name bijection, we could try to use name.hash
  //  instead -- could we? Seems like we could, but for additional cost, because hash does not
  //  identify unique name, hence we'll need additional check on the top of if: get fileId -> get
  //  its nameId -> get name -> check is it really equal.
  //  name -> hash -> (hash->fileId*) -> (fileId -> nameId -> fileName)* -> (check fileName eq name)

  private final Int2IntMultimap nameHashToFileId;

  public InvertedFilenameHashBasedIndex() {
    this(1 << 14);
  }

  public InvertedFilenameHashBasedIndex(int initialCapacity) {
    nameHashToFileId = new Int2IntMultimap(initialCapacity, 0.4f);
  }

  public boolean likelyFilesWithNames(@NotNull Set<String> names,
                                      @NotNull IntPredicate fileIdProcessor) {
    for (String name : names) {
      int hash = hashCodeOf(name);
      if (!nameHashToFileId.lookup(hash, fileIdProcessor)) {
        return false;
      }
    }
    return true;
  }

  public void updateFileName(int fileId,
                             @NotNull String oldName,
                             @NotNull String newName) {
    removeFileName(fileId, oldName);
    addFileName(fileId, newName);
  }

  public void removeFileName(int fileId,
                             @NotNull String oldName) {
    int oldHash = hashCodeOf(oldName);
    nameHashToFileId.remove(oldHash, fileId);
  }

  public void addFileName(int fileId,
                          @NotNull String newName) {
    int newHash = hashCodeOf(newName);
    nameHashToFileId.put(newHash, fileId);
  }

  private static int hashCodeOf(@NotNull String oldName) {
    int hash = oldName.hashCode();
    if (hash == Int2IntMultimap.NO_VALUE) {
      //Int2IntMultimap doesn't allow 0 keys/values, hence replace 0 hash with just any value!=0. Hash doesn't
      // identify name uniquely anyway, hence this replacement just adds another hash collision -- basically,
      // we replaced original String hashcode with our own, which avoids 0 at the cost of slightly higher chances
      // of collisions
      return -1;// any value!=0 will do
    }
    return hash;
  }
}
