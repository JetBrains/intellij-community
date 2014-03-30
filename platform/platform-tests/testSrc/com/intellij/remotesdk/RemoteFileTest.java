/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.remotesdk;

import com.intellij.remote.RemoteFile;
import com.intellij.remote.RemoteSdkCredentialsHolder;
import junit.framework.TestCase;

/**
 * @author traff
 */
public class RemoteFileTest extends TestCase {
  public void testExtractPathFromFullRemotePath() {
    assertEquals("/home/user", RemoteSdkCredentialsHolder.getInterpreterPathFromFullPath("ssh://user@server:8080/home/user"));
    assertEquals("C:\\Windows", RemoteSdkCredentialsHolder.getInterpreterPathFromFullPath("ssh://user@server:8080C:\\Windows"));
    assertEquals("/home/a@b", RemoteSdkCredentialsHolder.getInterpreterPathFromFullPath("ssh://a@b@server:8080/home/a@b"));
    assertEquals("/home/a/b", RemoteSdkCredentialsHolder.getInterpreterPathFromFullPath("ssh://a/b@server:8080/home/a/b"));
  }

  public void testIsWindowsPath() {
    assertTrue(RemoteFile.isWindowsPath("ssh://user@server:8080C:\\Windows"));
    assertTrue(RemoteFile.isWindowsPath("ssh://user@server:8080X:"));
    assertFalse(RemoteFile.isWindowsPath("ssh://user@server:8080/home/user"));
    assertFalse(RemoteFile.isWindowsPath("ssh://a\\b@server:8080/home/user"));
    assertFalse(RemoteFile.isWindowsPath("ssh://a\\b@server:8080/home/\\"));
  }
}
