/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.util.io

import com.intellij.openapi.util.io.FileUtil
import junit.framework.TestCase
import java.io.File

class CompressedAppendableFileTest() : TestCase() {
  @Throws
  fun testCreateParentDirWhenSave() {
    val randomTemporaryPath = File(FileUtil.generateRandomTemporaryPath(), "Test.compressed")
    try {
      val enumerator = CompressedAppendableFile(randomTemporaryPath)
      val byteArray: ByteArray = ByteArray(1)
      enumerator.append(byteArray, 1)
      enumerator.force();
      enumerator.dispose()
    } finally {
      FileUtil.delete(randomTemporaryPath.parentFile)
    }
  }
}