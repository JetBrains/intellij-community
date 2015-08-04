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
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.io.IOException

public class TemporaryDirectory : ExternalResource() {
  private val files = SmartList<File>()

  private var sanitizedName: String? = null

  override fun apply(base: Statement, description: Description): Statement {
    sanitizedName = FileUtil.sanitizeName(description.getMethodName())
    return super.apply(base, description)
  }

  override fun after() {
    for (file in files) {
      FileUtil.delete(file)
    }
    files.clear()
  }

  public fun newDirectory(directoryName: String? = null): File {
    val tempDirectory = FileUtilRt.getTempDirectory()
    var testFileName = sanitizedName!!
    if (directoryName != null) {
      testFileName += "_$directoryName"
    }

    var file = File(tempDirectory, testFileName)
    var i = 0
    while (file.exists() && i < 9) {
      file = File(tempDirectory, "${testFileName}_$i")
      i++
    }

    if (file.exists()) {
      throw IOException("Couldn't generate unique random path")
    }
    files.add(file)
    return file
  }
}
