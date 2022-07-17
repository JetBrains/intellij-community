// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import com.intellij.testFramework.rules.TempDirectory;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class CoreLocalFileSystemTest {
  @Rule public TempDirectory tempDir = new TempDirectory();

  private final CoreLocalFileSystem myLocalFs = new CoreLocalFileSystem();

  @Test
  public void basics() throws IOException {
    Path dir = tempDir.newDirectory("dir").toPath();
    Path f1 = Files.writeString(dir.resolve("f1"), "123");

    VirtualFile vd = myLocalFs.findFileByNioFile(dir);
    VirtualFile vf1 = myLocalFs.findFileByPath(f1.toString());

    assertThat(vd).isNotNull();
    assertThat(vd.getFileSystem()).isSameAs(myLocalFs);
    assertThat(vd.getName()).isEqualTo(dir.getFileName().toString());
    assertThat(vd.isWritable()).isFalse();
    assertThat(vd.isDirectory()).isTrue();
    assertThat(vd.getChildren()).hasSize(1).isEqualTo(new VirtualFile[]{vf1});

    assertThat(vf1).isNotNull();
    assertThat(vf1.getPath()).endsWith("/dir/f1");
    assertThat(vf1.isWritable()).isFalse();
    assertThat(vf1.isDirectory()).isFalse();
    assertThat(vf1.getTimeStamp()).isEqualTo(Files.getLastModifiedTime(f1).toMillis());
    assertThat(vf1.getLength()).isEqualTo(Files.size(f1));
    assertThat(vf1.getParent()).isEqualTo(vd);
    assertThat(vf1.getChildren()).isEmpty();
    assertThat(vf1.contentsToByteArray()).isEqualTo(new byte[]{'1', '2', '3'});
  }
}
