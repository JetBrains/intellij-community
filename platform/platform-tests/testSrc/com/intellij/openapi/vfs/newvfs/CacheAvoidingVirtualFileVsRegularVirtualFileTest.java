// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.testFramework.junit5.TestApplication;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static com.intellij.openapi.vfs.newvfs.persistent.FSRecords.MIN_REGULAR_FILE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;


/**
 * Test checks how do regular (cachable) VirtualFile's properties compare to appropriate {@link CacheAvoidingVirtualFile}'s
 * properties, for both {@link CacheAvoidingVirtualFileWrapper} and {@link TransientVirtualFileImpl} implementations.
 * <p>
 * The idea of this test is to scan through all the files in VFS. VFS content will be quite different depending on the
 * tests being run before that. But the whole idea is that this shouldn't affect the test results in any way.
 */
@TestApplication
public class CacheAvoidingVirtualFileVsRegularVirtualFileTest {

  @Test
  void regularVirtualFiles_areEqualsHashCodeCompatible_WithTheirCacheAvoidingWrappers() {
    PersistentFS pFS = PersistentFS.getInstance();
    forManyVirtualFilesFromVFS(pFS, regularFile -> {
      CacheAvoidingVirtualFileWrapper cacheAvoidingFile = new CacheAvoidingVirtualFileWrapper((NewVirtualFile)regularFile);

      assertEquals(regularFile.hashCode(), cacheAvoidingFile.hashCode(),
                   "Cache-avoiding wrapper .hashCode() must be equal to its cacheable counterpart");
      //try both sides (i.e. check commutativity):
      assertEquals(regularFile, cacheAvoidingFile,
                   "Cacheable file must be equal to its cache-avoiding counterpart");
      assertEquals(cacheAvoidingFile, regularFile,
                   "Cache-avoiding wrapper file must be equal to its cacheable counterpart");
    });
  }

  @Test
  void regularVirtualFiles_areNotEqualAndHashCodeCompatible_WithTheirTransientCounterparts() {
    PersistentFS pFS = PersistentFS.getInstance();
    forManyVirtualFilesFromVFS(pFS, regularFile -> {

      VirtualFile parent = regularFile.getParent();
      if (parent == null) return;

      TransientVirtualFileImpl transientFile = new TransientVirtualFileImpl(
        regularFile.getName(),
        regularFile.getPath(),
        (NewVirtualFileSystem)regularFile.getFileSystem(),
        parent
      );

      assertNotEquals(regularFile.hashCode(), transientFile.hashCode(),
                      "regular VirtualFile.hashCode() must NOT be equal to its Transient counterpart");
      //try both sides (i.e. check commutativity):
      assertNotEquals(regularFile, transientFile,
                      "Regular file must be equal to its transient counterpart");
      assertNotEquals(transientFile, regularFile,
                      "Transient VirtualFile must be equal to its regular counterpart");
    });
  }

  @Test
  void regularVirtualFilesProperties_areEqualToAppropriateTransientVirtualFileProperties() {
    PersistentFS pFS = PersistentFS.getInstance();
    forManyVirtualFilesFromVFS(pFS, regularFile -> {
      VirtualFile parent = regularFile.getParent();
      if (parent == null) return;

      parent.refresh(false, false);//ensure cached data is fresh

      TransientVirtualFileImpl transientFile = new TransientVirtualFileImpl(
        regularFile.getName(),
        regularFile.getPath(),
        (NewVirtualFileSystem)regularFile.getFileSystem(),
        parent
      );

      assertEquals(regularFile.exists(), transientFile.exists(),
                   "regular VirtualFile[" + regularFile.getPath() + "].exists() must be equal to its Transient counterpart");
      if (regularFile.isValid()) {
        assertEquals(regularFile.isDirectory(), transientFile.isDirectory(),
                     "regular VirtualFile[" + regularFile.getPath() + "].isDirectory() must be equal to its Transient counterpart");
        assertEquals(regularFile.isWritable(), transientFile.isWritable(),
                     "regular VirtualFile[" + regularFile.getPath() + "].isWriteable() must be equal to its Transient counterpart");

        if (!regularFile.isDirectory()) {//type/length is undefined for directories
          assertEquals(regularFile.getLength(), transientFile.getLength(),
                       "regular VirtualFile[" + regularFile.getPath() + "].getLength() must be equal to its Transient counterpart");

          //file-type detection is quite tangled: sometimes it relies on fileId, persistent VFS file attributes, etc
          // For transient files file-type sometimes can't be detected
          if (transientFile.getFileType() != UnknownFileType.INSTANCE) {
            assertEquals(regularFile.getFileType(), transientFile.getFileType(),
                         "regular VirtualFile[" + regularFile.getPath() + "].getFileType() must be equal to its Transient counterpart");
          }
        }
      }
    });
  }

  /** In TC runs VFS could be quite big, so we need to limit the number of files to test. */
  private static final int MAX_FILES_TO_TEST = 50_000;

  private static void forManyVirtualFilesFromVFS(@NotNull PersistentFS pFS,
                                                 @NotNull Consumer<VirtualFile> consumer) {
    int maxFileId = ((PersistentFSImpl)pFS).peer().connection().records().maxAllocatedID();
    if (maxFileId <= MIN_REGULAR_FILE_ID) {
      return;//nothing to iterate
    }
    int step = 104729;//big prime number
    for (int i = 0; i < MAX_FILES_TO_TEST; i++) {
      int fileId = MIN_REGULAR_FILE_ID + Math.abs(i * step) % (maxFileId - MIN_REGULAR_FILE_ID);
      VirtualFile regularFile = pFS.findFileById(fileId);
      if (regularFile == null) continue;

      assertThat(regularFile)
        .describedAs("findFileById(" + fileId + "): must always return 'regular' (not cache-avoiding) VirtualFile")
        .isNotInstanceOf(CacheAvoidingVirtualFile.class);

      consumer.accept(regularFile);
    }
  }
}
