// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io

import com.intellij.openapi.util.SystemInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PathExecLazyValueTest {
  @Test fun positive() {
    val shell = File(if (SystemInfo.isWindows) "C:\\Windows\\System32\\cmd.exe" else "/bin/sh")
    assertTrue(shell.canExecute())
    assertTrue(PathExecLazyValue.create(shell.name).value)
  }

  @Test fun negative() {
    assertFalse(PathExecLazyValue.create("no-one-in-his-right-mind-names-an-exec-like-this").value)
  }

  @Test(expected = IllegalArgumentException::class) fun contract() {
    PathExecLazyValue.create("bad\\path")
  }
}