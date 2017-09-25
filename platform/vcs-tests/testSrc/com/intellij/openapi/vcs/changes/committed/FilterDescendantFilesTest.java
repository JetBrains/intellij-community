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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilterDescendantVirtualFiles;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.PlatformTestCase;
import junit.framework.Assert;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FilterDescendantFilesTest extends PlatformTestCase {
  @Test
  public void testSecondModuleSameLevelAsProject() {
    final File tmpDir = createDir(new File(FileUtil.getTempDirectory()), "tmpDir");
    final File child1 = createDir(tmpDir, "child1");
    final File child2 = createDir(tmpDir, "child2");

    final List<VirtualFile> list = convert(new File[]{child2, child2, child1});
    Assert.assertEquals(3, list.size());
    FilterDescendantVirtualFiles.filter(list);
    Assert.assertEquals(2, list.size());
  }

  @Test
  public void testUsual() {
    File tmp = new File(FileUtil.getTempDirectory());
    final File tmpDir = createDir(tmp, "tmpDir");
    final File child1 = createDir(tmpDir, "child1");
    final File child2 = createDir(tmp, "child2");

    final List<VirtualFile> list = convert(new File[]{tmpDir, child2, child1});
    Assert.assertEquals(3, list.size());
    FilterDescendantVirtualFiles.filter(list);
    Assert.assertEquals(2, list.size());
  }

  private final List<VirtualFile> convert(final File[] files) {
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

  private File createDir(final File parent, final String name) {
    final File result = new File(parent, name);
    for (int i = 0; i < 100; i++) {
      if (result.mkdirs()) break;
    }

    myFilesToDelete.add(result);
    ApplicationManager.getApplication().runWriteAction(() -> {
      VirtualFileManager.getInstance().syncRefresh();
    });
    return result;
  }
}
