/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic.hprof

import com.intellij.diagnostic.hprof.util.FileBackedIntList
import com.intellij.diagnostic.hprof.util.IntList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

class FileBackedIntListTest {

  private lateinit var list: IntList
  private lateinit var channel: FileChannel
  private val tmpFolder: TemporaryFolder = TemporaryFolder()

  @Before
  fun setUp() {
    tmpFolder.create()
    channel = FileChannel.open(tmpFolder.newFile().toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE)
    list = FileBackedIntList.createEmpty(channel, 10)
    list[0] = 90
    list[5] = 95
    list[9] = 99
  }

  @After
  fun tearDown() {
    channel.close()
    tmpFolder.delete()
  }

  @Test
  fun get() {
    assertEquals(list[0], 90)
    assertEquals(list[9], 99)
    assertEquals(list[5], 95)
    assertEquals(list[0], 90)
  }

  @Test
  fun getFromEmptyIndex() {
    assertEquals(list[1], 0)
    assertEquals(list[2], 0)
  }

  @Test(expected = java.nio.BufferUnderflowException::class)
  fun getOutOfBounds() {
    list[10]
  }

  @Test
  fun set() {
    list[0] = Int.MAX_VALUE
    assertEquals(Int.MAX_VALUE, list[0])
  }
}