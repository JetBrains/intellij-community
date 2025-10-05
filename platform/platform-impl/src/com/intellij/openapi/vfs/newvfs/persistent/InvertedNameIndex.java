// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.util.io.DataEnumerator;
import it.unimi.dsi.fastutil.ints.IntCollection;
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

  /**
   * Iterates through all the fileIds associated with nameId from nameIds collection, and passes each fileId to fileIdProcessor.
   *
   * @return if fileIdProcessor returns false -> stop eagerly, and return false, otherwise return true (even if there were no fileId
   * to process!)
   */
  @VisibleForTesting
  boolean forEachFileIds(@NotNull IntCollection nameIds,
                         @NotNull IntPredicate fileIdProcessor);

  /**
   * Updates a fileName for a fileId: replaces oldNameId with newNameId.
   * Conceptually: removes fileId from the list of fileIds associated with oldNameId, and adds it to the list of fileIds associated
   * with newNameId.<p/>
   * Old/new nameId could be {@link #NULL_NAME_ID}:
   * <pre>
   * updateFileName(fileId, NULL_NAME_ID, nameId);        //inserts _new_ [nameId->fileId] mapping
   * updateFileName(fileId, nameId,       NULL_NAME_ID);  //removes fileId mapping
   * </pre>
   */
  void updateFileName(int fileId, int oldNameId, int newNameId);

  /** Clears all the mappings */
  void clear();

  /** To be called in tests: checks internal invariants, if any, are held */
  @VisibleForTesting
  void checkConsistency();
}
