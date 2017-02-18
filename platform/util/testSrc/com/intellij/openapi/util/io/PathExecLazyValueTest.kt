/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
    assertTrue(PathExecLazyValue(shell.name).value)
  }

  @Test fun negative() {
    assertFalse(PathExecLazyValue("no-one-in-his-right-mind-names-an-exec-like-this").value)
  }

  @Test(expected = IllegalArgumentException::class) fun contract() {
    PathExecLazyValue("bad\\path")
  }
}