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
package com.intellij.util.io;

import com.google.common.collect.Sets;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileFilter;
import java.util.Set;

/**
 * @author traff
 */
public class TarUtilTest {

  @Test
  public void testTarArchiveExtract() throws Exception {
    File tempDir = FileUtil.createTempDirectory("tarOutputStream-util-test-", null);

    File tarFile = new File(tempDir, "test-tar-util.tar.gz");

    TarArchiveOutputStream tarOutputStream = TarUtil.getTarGzOutputStream(tarFile);

    Set<String> paths = Sets.newHashSet();

    TarUtil.addFileOrDirRecursively(tarOutputStream, null, getTestDataPath(), "",
                                    (FileFilter)pathname -> !pathname.getName().endsWith(".zip"), paths);

    tarOutputStream.close();

    Assert.assertEquals(2, paths.size());

    Assert.assertTrue(paths.stream().noneMatch(f -> f.endsWith(".zip")));

    try {
      TarUtil.extract(tarFile, tempDir, null);

      tarFile.delete();

      checkFileStructure(tempDir,
                         TestFileSystemBuilder.fs()
                           .file("a.txt")
                           .dir("dir").file("b.txt"));
    }
    finally {
      tempDir.delete();
    }
  }

  private static void checkFileStructure(@NotNull File parentDir, @NotNull TestFileSystemBuilder expected) {
    expected.build().assertDirectoryEqual(parentDir);
  }

  @NotNull
  private static File getTestDataPath() {
    File communityDir = new File(PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/'));
    return new File(communityDir, "platform/util/testData/tar");
  }
}
