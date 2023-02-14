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

import com.intellij.diagnostic.hprof.util.FileBackedHashMap
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

class FileBackedHashMapTest {

  private lateinit var map: FileBackedHashMap
  private lateinit var channel: FileChannel
  private val tmpFolder: TemporaryFolder = TemporaryFolder()

  @Before
  fun setUp() {
    tmpFolder.create()
    channel = FileChannel.open(tmpFolder.newFile().toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE)
    map = FileBackedHashMap.createEmpty(channel, 10, 4, 4)
    map.put(10).putInt(110)
    map.put(11).putInt(111)
    map.put(20).putInt(120)
    map.put(9).putInt(19)
    map.put(1000).putInt(11000)
    map.put(1234).putInt(11234)
  }

  @After
  fun tearDown() {
    channel.close()
    tmpFolder.delete()
  }

  @Test
  fun get() {
    assertEquals(110, map[10]!!.int)
    assertEquals(111, map[11]!!.int)
    assertEquals(110, map[10]!!.int)
    assertEquals(19, map[9]!!.int)
    assertEquals(11000, map[1000]!!.int)
    assertNull(map[8])
    assertNull(map[18])
    assertNull(map[0])
  }

  @Test
  fun put() {
    map.put(1000).putInt(12000)
    assertEquals(12000, map[1000]!!.int)
  }

  @Test
  fun containsKey() {
    assertTrue(map.containsKey(10))
    assertTrue(map.containsKey(11))
    assertTrue(map.containsKey(9))
    assertFalse(map.containsKey(18))
  }
}