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

import com.intellij.diagnostic.hprof.classstore.HProfMetadata
import com.intellij.diagnostic.hprof.histogram.Histogram
import com.intellij.diagnostic.hprof.navigator.ObjectNavigator
import com.intellij.diagnostic.hprof.parser.HProfEventBasedParser
import com.intellij.diagnostic.hprof.visitors.RemapIDsVisitor
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.function.LongUnaryOperator

class ObjectNavigatorTest {

  private val tmpFolder: TemporaryFolder = TemporaryFolder()

  @Before
  fun setUp() {
    tmpFolder.create()
  }

  @After
  fun tearDown() {
    tmpFolder.delete()
  }

  private fun openTempEmptyFileChannel(): FileChannel {
    return FileChannel.open(tmpFolder.newFile().toPath(),
                            StandardOpenOption.READ,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.DELETE_ON_CLOSE)
  }

  @Test
  fun testNameClash() {
    val hprofFile = tmpFolder.newFile()
    class MyTestClass1 {
      val field: String = ""
    }
    class MyTestClass2 {
      val field: String = ""
    }

    var object1Id: Long = 0
    var object2Id: Long = 0

    val scenario: HProfBuilder.() -> Unit = {
      object1Id = addObject(MyTestClass1())
      object2Id = addObject(MyTestClass2())
    }
    val classNameMapping: (Class<*>) -> String = { c ->
      if (c == MyTestClass1::class.java ||
          c == MyTestClass2::class.java) {
        "MyTestClass"
      } else {
        c.name
      }
    }
    HProfTestUtils.createHProfOnFile(hprofFile,
                                     scenario,
                                     classNameMapping)
    val (navigator, remap) = getObjectNavigatorAndRemappingFunction(hprofFile)
    val clashedClassNames = ArrayList<String>()

    // Check that each class got assigned a unique name
    clashedClassNames.add(navigator.getClassForObjectId(remap.applyAsLong(object1Id)).name)
    clashedClassNames.add(navigator.getClassForObjectId(remap.applyAsLong(object2Id)).name)
    clashedClassNames.sort()

    assertArrayEquals(arrayOf("MyTestClass!1", "MyTestClass!2"), clashedClassNames.toArray())

    // Verify goToInstanceField works correctly for classes with name clash
    navigator.goTo(remap.applyAsLong(object1Id))
    navigator.goToInstanceField("MyTestClass", "field")

    assertEquals("java.lang.String", navigator.getClass().undecoratedName)
  }

  private fun getObjectNavigatorAndRemappingFunction(hprofFile: File): Pair<ObjectNavigator, LongUnaryOperator> {
    FileChannel.open(hprofFile.toPath(), StandardOpenOption.READ).use { hprofChannel ->
      val parser = HProfEventBasedParser(hprofChannel)
      val hprofMetadata = HProfMetadata.create(parser)
      val histogram = Histogram.create(parser, hprofMetadata.classStore)

      val remapIDsVisitor = RemapIDsVisitor.createMemoryBased()
      parser.accept(remapIDsVisitor, "id mapping")
      parser.setIdRemappingFunction(remapIDsVisitor.getRemappingFunction())
      hprofMetadata.remapIds(remapIDsVisitor.getRemappingFunction())

      return Pair(ObjectNavigator.createOnAuxiliaryFiles(
        parser,
        openTempEmptyFileChannel(),
        openTempEmptyFileChannel(),
        hprofMetadata,
        histogram.instanceCount
      ), remapIDsVisitor.getRemappingFunction())
    }
  }

}
