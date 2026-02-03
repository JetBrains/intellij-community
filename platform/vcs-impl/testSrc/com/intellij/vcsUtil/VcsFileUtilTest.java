// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcsUtil;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ApplicationRule;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Test;

import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class VcsFileUtilTest {
  @ClassRule public static final ApplicationRule appRule = new ApplicationRule();

  @Test
  public void unescape_cyrillic() {
    String charsetMessage = " Default charset: " + Charset.defaultCharset();
    assertEquals("Cyrillic folder was unescaped incorrectly." + charsetMessage,
                 "папка/file.txt",
                 VcsFileUtil.unescapeGitPath("\\320\\277\\320\\260\\320\\277\\320\\272\\320\\260/file.txt"));
    assertEquals("Cyrillic folder with file name were unescaped incorrectly." + charsetMessage,
                 "папка/документ",
                 VcsFileUtil.unescapeGitPath(
                   "\\320\\277\\320\\260\\320\\277\\320\\272\\320\\260/\\320\\264\\320\\276\\320\\272\\321\\203\\320\\274\\320\\265\\320\\275\\321\\202"));
  }

  @Test
  public void testWindowsFilePaths() {
    Assume.assumeTrue(SystemInfo.isWindows);

    VirtualFile file = LocalFileSystem.getInstance().findFileByPath("C:/");
    Assert.assertNotNull(file);

    assertEquals("C:/", VcsUtil.getFilePath(file).getPath());
    assertEquals("C:/", VcsUtil.getFilePath("C:/", true).getPath());
    assertEquals("C:/", VcsUtil.getFilePath("C:\\", true).getPath());
    assertEquals("C:", VcsUtil.getFilePath("C:", true).getPath());
    assertNull(VcsUtil.getFilePath("C:", true).getParentPath());

    FilePath homePath = VcsUtil.getFilePath("C:\\home\\path", true);
    assertEquals("C:/home/path", homePath.getPath());
    assertEquals("C:/home", homePath.getParentPath().getPath());
    assertEquals("C:/", homePath.getParentPath().getParentPath().getPath());
    assertNull(homePath.getParentPath().getParentPath().getParentPath());

    FilePath wslHomePath = VcsUtil.getFilePath("\\\\wsl$\\ubuntu\\home", true);
    assertEquals("//wsl$/ubuntu/home", wslHomePath.getPath());
    assertEquals("//wsl$/ubuntu", wslHomePath.getParentPath().getPath());
    assertNull(wslHomePath.getParentPath().getParentPath());
  }

  @Test
  public void testLinuxFilePaths() {
    Assume.assumeTrue(SystemInfo.isUnix);

    VirtualFile file = LocalFileSystem.getInstance().findFileByPath("/");
    Assert.assertNotNull(file);

    assertEquals("/", VcsUtil.getFilePath(file).getPath());
    assertEquals("/", VcsUtil.getFilePath("/", true).getPath());
    assertEquals("/", VcsUtil.getFilePath("//", true).getPath());
    assertEquals("/", VcsUtil.getFilePath("///", true).getPath());
    assertNull(VcsUtil.getFilePath("/", true).getParentPath());

    FilePath homePath = VcsUtil.getFilePath("/home/path", true);
    assertEquals("/home/path", homePath.getPath());
    assertEquals("/home", homePath.getParentPath().getPath());
    assertEquals("/", homePath.getParentPath().getParentPath().getPath());
    assertNull(homePath.getParentPath().getParentPath().getParentPath());
  }
}