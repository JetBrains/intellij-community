// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilterDescendantVirtualFiles;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FilterDescendantFilesTest extends HeavyPlatformTestCase {
  public void testSecondModuleSameLevelAsProject() {
    File tmpDir = createDir(new File(FileUtil.getTempDirectory()), "tmpDir");
    File child1 = createDir(tmpDir, "child1");
    File child2 = createDir(tmpDir, "child2");
    syncRefresh();

    final List<VirtualFile> list = convert(new File[]{child2, child2, child1});
    assertEquals(3, list.size());
    FilterDescendantVirtualFiles.filter(list);
    assertEquals(2, list.size());
  }

  public void testUsual() {
    File tmp = new File(FileUtil.getTempDirectory());
    File tmpDir = createDir(tmp, "tmpDir");
    File child1 = createDir(tmpDir, "child1");
    File child2 = createDir(tmp, "child2");
    syncRefresh();

    final List<VirtualFile> list = convert(new File[]{tmpDir, child2, child1});
    assertEquals(3, list.size());
    FilterDescendantVirtualFiles.filter(list);
    assertEquals(2, list.size());
  }

  private static List<VirtualFile> convert(final File[] files) {
    final List<VirtualFile> result = new ArrayList<>();
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    for (File file : files) {
      VirtualFile vf = lfs.findFileByIoFile(file);
      if (vf == null) {
        vf = lfs.refreshAndFindFileByIoFile(file);
      }
      if (vf != null) {
        result.add(vf);
      }
    }
    return result;
  }

  private static File createDir(@NotNull File parent, @NotNull String name) {
    final File result = new File(parent, name);
    for (int i = 0; i < 100; i++) {
      if (result.mkdirs()) {
        break;
      }
    }
    return result;
  }

  private static void syncRefresh() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      VirtualFileManager.getInstance().syncRefresh();
    });
  }
}
