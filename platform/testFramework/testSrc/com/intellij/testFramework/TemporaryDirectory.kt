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
package com.intellij.testFramework

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.SmartList
import org.junit.rules.ExternalResource
import java.io.File

public class TemporaryDirectory : ExternalResource() {
  private val files = SmartList<File>()

  override fun after() {
    for (file in files) {
      FileUtil.delete(file)
    }
    files.clear()
  }

  public fun newDirectory(): File {
    val file = FileUtilRt.generateRandomTemporaryPath()
    files.add(file)
    return file;
  }
}
