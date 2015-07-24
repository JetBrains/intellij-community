/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vfs

import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import kotlin.properties.Delegates
import kotlin.test.assertEquals
import org.junit.Before as before
import org.junit.Test as test

class ArchiveFileSystemPerformanceTest : BareTestFixtureTestCase() {
  private var fs: ArchiveFileSystem by Delegates.notNull()
  private var entry: VirtualFile by Delegates.notNull()

  before fun setUp() {
    fs = StandardFileSystems.jar() as ArchiveFileSystem
    entry = fs.findFileByPath("${PlatformTestUtil.getRtJarPath()}!/java/lang/Object.class")!!
  }

  test fun getRootByEntry() {
    val root = fs.getRootByEntry(entry)!!
    PlatformTestUtil.startPerformanceTest("ArchiveFileSystem.getRootByEntry()", 500, {
      for (i in 0..100000) {
        assertEquals(root, fs.getRootByEntry(entry))
      }
    }).cpuBound().assertTiming()
  }

  test fun getLocalByEntry() {
    val local = fs.getLocalByEntry(entry)!!
    PlatformTestUtil.startPerformanceTest("ArchiveFileSystem.getLocalByEntry()", 700, {
      for (i in 0..100000) {
        assertEquals(local, fs.getLocalByEntry(entry))
      }
    }).cpuBound().assertTiming()
  }
}