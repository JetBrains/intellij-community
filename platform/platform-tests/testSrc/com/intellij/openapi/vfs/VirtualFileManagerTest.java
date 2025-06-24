// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.vfs.newvfs.CacheAvoidingVirtualFile;
import com.intellij.testFramework.junit5.TestApplication;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

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

  @Test
  void findUrlWithoutCaching_doesNotCacheFilesInVFS(@TempDir Path tempDirectory) throws IOException {
    //create a file tree:
    for (String relativePath : CHILDREN) {
      Files.createDirectories(tempDirectory.resolve(relativePath));
    }

    String tempFolderUrl = tempDirectory.toUri().toString();

    VirtualFileManager vfm = VirtualFileManager.getInstance();
    VirtualFile tempVFile = vfm.findFileByUrlWithoutCaching(tempFolderUrl);
    assertInstanceOf(CacheAvoidingVirtualFile.class, tempVFile,
                     ".findFileByUrlWithoutCaching() should return CacheAvoidingVirtualFile");

    List<String> paths = collectAllPaths(tempVFile);

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

    VirtualFileManager vfm = VirtualFileManager.getInstance();

    vfm.findFileByUrl(tempFolderUrl);//enforce temp directory to be cached in VFS

    VirtualFile tempVFile = vfm.findFileByUrlWithoutCaching(tempFolderUrl);
    assertInstanceOf(CacheAvoidingVirtualFile.class, tempVFile,
                     ".findFileByUrlWithoutCaching() should return CacheAvoidingVirtualFile");

    List<String> paths = collectAllPaths(tempVFile);
    paths.remove(tempDirectory.toString());//because it is cached in VFS

    LocalFileSystem localFS = LocalFileSystem.getInstance();
    for (String path : paths) {
      VirtualFile cached = localFS.findFileByPathIfCached(path);
      assertNull(cached, () -> "[" + path + "] should NOT be cached");
    }
  }

  //TODO RC: test all children starting from cache-avoiding are cache-avoiding
  //TODO RC: test cache-avoiding file == regular file, if actual file is cached

  //<editor-fold desc="Helpers"> ====================================================================================

  /** @return paths of all the files under the given root (including the root itself) */
  private static @NotNull List<String> collectAllPaths(VirtualFile root) {
    List<String> paths = new ArrayList<>();
    collectPathsRecursive(root, paths);
    return paths;
  }

  private static void collectPathsRecursive(VirtualFile vFile,
                                            Collection<String> paths) {
    for (VirtualFile child : vFile.getChildren()) {
      paths.add(child.getPath());

      if (child.isDirectory()) {
        collectPathsRecursive(child, paths);
      }
    }
  }
  //</editor-fold> ===================================================================================================
}
