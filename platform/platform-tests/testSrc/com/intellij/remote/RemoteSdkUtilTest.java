// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RemoteSdkUtilTest {
  @Test
  public void isValidLinuxEnvName() {
    assertTrue(com.intellij.remote.RemoteSdkUtil.isInvalidLinuxEnvName(null));
    assertTrue(com.intellij.remote.RemoteSdkUtil.isInvalidLinuxEnvName("=::"));
    assertFalse(com.intellij.remote.RemoteSdkUtil.isInvalidLinuxEnvName("APPDATA"));
    assertFalse(RemoteSdkUtil.isInvalidLinuxEnvName("ComSpec"));
  }
}
