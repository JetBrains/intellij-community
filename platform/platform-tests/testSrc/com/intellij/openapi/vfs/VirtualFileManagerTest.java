// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.vfs.newvfs.CacheAvoidingVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.testFramework.junit5.TestApplication;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@TestApplication
public class VirtualFileManagerTest {


  private static final String[] CHILDREN = {
    "a", "a/a", "a/a/a",
    "b", "b/b", "b/b/b",
    "c", "c/c", "c/c/c",
    "d", "d/d", "d/d/d",
    "e", "e/e", "e/e/e",
    "f", "f/f", "f/f/f"
  };

  private final VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();

  @Test
  void findUrlWithoutCaching_doesNotCacheFilesInVFS(@TempDir Path tempDirectory) throws IOException {
    //create a file tree:
    for (String relativePath : CHILDREN) {
      Files.createDirectories(tempDirectory.resolve(relativePath));
    }

    String tempFolderUrl = tempDirectory.toUri().toString();

    VirtualFile tempVFile = virtualFileManager.findFileByUrlWithoutCaching(tempFolderUrl);
    assertInstanceOf(CacheAvoidingVirtualFile.class, tempVFile,
                     ".findFileByUrlWithoutCaching() should return CacheAvoidingVirtualFile");

    List<String> paths = collectAllChildrenPaths(tempVFile);

    LocalFileSystem localFS = LocalFileSystem.getInstance();
    for (String path : paths) {
      VirtualFile cached = localFS.findFileByPathIfCached(path);
      assertNull(cached, () -> "[" + path + "] shouldn't be cached");
    }
  }

  @Test
  void findUrlWithoutCaching_doesNotCacheFilesInVFS_EvenIfTopDirectoryIsCached(@TempDir Path tempDirectory) throws IOException {
    //create a file tree:
    for (String relativePath : CHILDREN) {
      Files.createDirectories(tempDirectory.resolve(relativePath));
    }

    String tempFolderUrl = tempDirectory.toUri().toString();

    virtualFileManager.findFileByUrl(tempFolderUrl);//enforce temp directory to be cached in VFS

    VirtualFile tempVFile = virtualFileManager.findFileByUrlWithoutCaching(tempFolderUrl);
    assertInstanceOf(CacheAvoidingVirtualFile.class, tempVFile,
                     ".findFileByUrlWithoutCaching() should return CacheAvoidingVirtualFile");

    List<String> paths = collectAllChildrenPaths(tempVFile);
    paths.remove(tempDirectory.toString());//because it is cached in VFS

    LocalFileSystem localFS = LocalFileSystem.getInstance();
    for (String path : paths) {
      VirtualFile cached = localFS.findFileByPathIfCached(path);
      assertNull(cached, () -> "[" + path + "] should NOT be cached");
    }
  }

  @Test
  void allChildrenStartingFromCacheAvoidingDirectory_areAlsoCacheAvoiding(@TempDir Path tempDirectory) throws IOException {
    //create a file tree:
    for (String relativePath : CHILDREN) {
      Files.createDirectories(tempDirectory.resolve(relativePath));
    }

    String tempFolderUrl = tempDirectory.toUri().toString();

    virtualFileManager.findFileByUrl(tempFolderUrl);//enforce temp directory to be cached in VFS

    VirtualFile tempVFile = virtualFileManager.findFileByUrlWithoutCaching(tempFolderUrl);
    assertInstanceOf(CacheAvoidingVirtualFile.class, tempVFile,
                     ".findFileByUrlWithoutCaching() should return CacheAvoidingVirtualFile");

    walkTree(tempVFile, file -> {
      assertInstanceOf(CacheAvoidingVirtualFile.class, file,
                       "Every children of cache-avoiding parent should be cache-avoiding, but : " + file.getPath());
    });
  }

  @Test
  void cacheAvoidingWrapperVFiles_AreEqualToRegularCounterparts(@TempDir Path tempDirectory) throws IOException {
    //create a file tree:
    for (String relativePath : CHILDREN) {
      Files.createDirectories(tempDirectory.resolve(relativePath));
    }

    String tempFolderUrl = tempDirectory.toUri().toString();

    virtualFileManager.findFileByUrl(tempFolderUrl);//enforce temp directory to be cached in VFS

    VirtualFile tempVFile = virtualFileManager.findFileByUrlWithoutCaching(tempFolderUrl);
    assertInstanceOf(CacheAvoidingVirtualFile.class, tempVFile,
                     ".findFileByUrlWithoutCaching() should return CacheAvoidingVirtualFile");

    walkTree(tempVFile, vFile -> {
      //vFile is likely not just cacheAvoiding, but TransientVirtualFile, and TransientVirtualFiles are not equal to their
      // regular counterparts, so we do back-and-forth transition, to ensure regular vs CacheAvoidingWrapper comparison:
      VirtualFile cacheableFile = ((CacheAvoidingVirtualFile)vFile).asCacheable();
      VirtualFile cacheAvoidingFile = ((NewVirtualFile)cacheableFile).asCacheAvoiding();

      assertEquals(cacheableFile.hashCode(), cacheAvoidingFile.hashCode(),
                   "Cache-avoiding wrapper .hashCode() must be equal to its cacheable counterpart");
      //try both sides (i.e. check commutativity):
      assertEquals(cacheableFile, cacheAvoidingFile,
                   "Cacheable file must be equal to its cache-avoiding counterpart");
      assertEquals(cacheAvoidingFile, cacheableFile,
                   "Cache-avoiding wrapper file must be equal to its cacheable counterpart");
    });
  }

  //<editor-fold desc="Helpers"> ====================================================================================

  /** @return paths of all the files under the given root (including the root itself) */
  private static @NotNull List<String> collectAllChildrenPaths(VirtualFile root) {
    List<String> paths = new ArrayList<>();
    walkTree(root, file -> paths.add(file.getPath()));
    return paths;
  }

  private static void walkTree(@NotNull VirtualFile vFile,
                               @NotNull Consumer<VirtualFile> consumer) {
    for (VirtualFile child : vFile.getChildren()) {
      consumer.accept(child);

      if (child.isDirectory()) {
        walkTree(child, consumer);
      }
    }
  }

  //</editor-fold> ===================================================================================================
}
