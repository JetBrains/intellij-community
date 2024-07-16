// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remotesdk;

import com.intellij.remote.RemoteFile;
import com.intellij.remote.RemoteSdkProperties;
import junit.framework.TestCase;

public class RemoteFileTest extends TestCase {
  public void testExtractPathFromFullRemotePath() {
    assertEquals("/home/user", RemoteSdkProperties.getInterpreterPathFromFullPath("ssh://user@server:8080/home/user"));
    assertEquals("C:\\Windows", RemoteSdkProperties.getInterpreterPathFromFullPath("ssh://user@server:8080C:\\Windows"));
    assertEquals("/home/a@b", RemoteSdkProperties.getInterpreterPathFromFullPath("ssh://a@b@server:8080/home/a@b"));
    assertEquals("/home/a/b", RemoteSdkProperties.getInterpreterPathFromFullPath("ssh://a/b@server:8080/home/a/b"));
  }

  public void testIsWindowsPath() {
    assertTrue(RemoteFile.isWindowsPath("ssh://user@server:8080C:\\Windows"));
    assertTrue(RemoteFile.isWindowsPath("ssh://user@server:8080X:"));
    assertFalse(RemoteFile.isWindowsPath("ssh://user@server:8080/home/user"));
    assertFalse(RemoteFile.isWindowsPath("ssh://a\\b@server:8080/home/user"));
    assertFalse(RemoteFile.isWindowsPath("ssh://a\\b@server:8080/home/\\"));
  }
}
