// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.jar.CoreJarFileSystem;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.io.Compressor;
import com.intellij.util.io.URLUtil;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

public class CoreJarFileSystemTest {
  @Rule public TempDirectory tempDir = new TempDirectory();

  private final CoreJarFileSystem myJarFS = new CoreJarFileSystem();

  @After
  public void tearDown() {
    myJarFS.clearHandlersCache();
  }

  @Test
  public void basics() throws IOException {
    File testJar = tempDir.newFile("test.jar");
    try (Compressor.Jar jar = new Compressor.Jar(testJar)) {
      jar.addManifest(new Manifest());
      jar.addDirectory("com");
      jar.addFile("java/util/ArrayList.class", new byte[]{(byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE});
    }

    VirtualFile root = myJarFS.findFileByPath(testJar.getPath() + URLUtil.JAR_SEPARATOR);
    assertThat(root).describedAs(testJar.getPath()).isNotNull();
    assertThat(root.getChildren()).hasSize(3);

    VirtualFile com = root.findFileByRelativePath("com");
    Assertions.<VirtualFile>assertThat(com).isNotNull().matches(f -> f.isDirectory());
    assertThat(com.getChildren()).isEmpty();

    VirtualFile arrayList = root.findFileByRelativePath("java/util/ArrayList.class");
    Assertions.<VirtualFile>assertThat(arrayList).isNotNull().matches(f -> !f.isDirectory());
    assertThat(arrayList.getChildren()).isEmpty();
  }
}
