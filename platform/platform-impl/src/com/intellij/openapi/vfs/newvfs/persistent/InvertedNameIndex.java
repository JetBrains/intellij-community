// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.util.io.DataEnumerator;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.function.IntPredicate;

/**
 * Index for lookup files by file name: [fileName -> fileId(s)], but nameId is used instead of fileName,
 * see {@link FSRecordsImpl#getNameId(String)}
 * <p/>
 * A replacement for {@link com.intellij.psi.search.FilenameIndex}.
 * <p/>
 * Conceptually it could be seen as Map[ nameId -> List[fileId1, fileId2, ...]] (=regular 'inverted index' structure), even
 * though specific implementation could choose other ways to actually store the data.
 */
@ApiStatus.Internal
public interface InvertedNameIndex {
  /** id=0 used as NULL (i.e. absent) value */
  int NULL_NAME_ID = DataEnumerator.NULL_ID;

  //TODO RC: replace with forEachFileId() -- more explicit
  default boolean processFilesWithNames(@NotNull IntList namesIds,
                                        @NotNull IntPredicate fileIdProcessor) {
    return forEachFileIds(namesIds, fileIdProcessor);
  }

  @VisibleForTesting
  boolean forEachFileIds(@NotNull IntCollection nameIds,
                         @NotNull IntPredicate fileIdProcessor);

  /**
   * Updates a fileName for a fileId: replaces oldNameId with newNameId.
   * Conceptually: removes fileId from the list of fileIds associated with oldNameId, and adds it to the list of fileIds associated
   * with newNameId.<p/>
   * Old/new nameId could be {@link #NULL_NAME_ID}:
   * <pre>
   * updateFileName(fileId, nameId, NULL_NAME_ID); //inserts _new_ [nameId->fileId] mapping
   * updateFileName(fileId, NULL_NAME_ID, nameId); //removes fileId mapping
   * </pre>
   */
  void updateFileName(int fileId, int newNameId, int oldNameId);

  void clear();

  @VisibleForTesting
  void checkConsistency();
}
