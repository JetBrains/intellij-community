// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.google.common.collect.Sets;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.rules.TempDirectory;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.Set;

/**
 * @author traff
 */
public class TarUtilTest {
  @Rule
  public TempDirectory tempDir = new TempDirectory();

  @Test
  public void createAndExtract() throws Exception {
    File tarFile = tempDir.newFile("test-tar-util.tar.gz");

    Set<String> paths = Sets.newHashSet();
    try (TarArchiveOutputStream tar = TarUtil.getTarGzOutputStream(tarFile)) {
      TarUtil.addFileOrDirRecursively(tar, null, getTestDataPath(), "", pathname -> !pathname.getName().endsWith(".zip"), paths);
    }
    Assert.assertEquals(2, paths.size());
    Assert.assertTrue(paths.stream().noneMatch(f -> f.endsWith(".zip")));

    File extracted = tempDir.newFolder("extracted");
    new Decompressor.Tar(tarFile).extract(extracted);
    checkFileStructure(extracted,
                       TestFileSystemBuilder.fs()
                         .file("a.txt")
                         .dir("dir").file("b.txt"));
  }

  private static void checkFileStructure(File parentDir, TestFileSystemBuilder expected) {
    expected.build().assertDirectoryEqual(parentDir);
  }

  private static File getTestDataPath() {
    return new File(PlatformTestUtil.getCommunityPath(), "platform/util/testData/tar");
  }
}