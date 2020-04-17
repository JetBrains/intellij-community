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

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.io.impl.*
import java.io.File
import java.nio.file.Path

/**
 * Builds a data structure specifying content (files, their content, sub-directories, archives) of a directory. It can be used to either check
 * that a given directory matches this specification or to generate files in a directory accordingly to the specification.
 */
inline fun directoryContent(content: DirectoryContentBuilder.() -> Unit): DirectoryContentSpec {
  val builder = DirectoryContentBuilderImpl(DirectorySpec())
  builder.content()
  return builder.result
}

/**
 * Builds a data structure specifying content (files, their content, sub-directories, archives) of a zip file. It can be used to either check
 * that a given zip file matches this specification or to generate a zip file accordingly to the specification.
 */
inline fun zipFile(content: DirectoryContentBuilder.() -> Unit): DirectoryContentSpec {
  val builder = DirectoryContentBuilderImpl(ZipSpec())
  builder.content()
  return builder.result
}

/**
 * Builds [DirectoryContentSpec] structure by an existing directory. Can be used to check that generated directory matched expected data
 * from testData directory.
 */
fun directoryContentOf(dir: Path): DirectoryContentSpec {
  return createSpecByDirectory(dir)
}

abstract class DirectoryContentBuilder {
  /**
   * File with name [name] and any content
   */
  abstract fun file(name: String)

  abstract fun file(name: String, text: String)

  abstract fun file(name: String, content: ByteArray)

  inline fun dir(name: String, content: DirectoryContentBuilder.() -> Unit) {
    val dirDefinition = DirectorySpec()
    DirectoryContentBuilderImpl(dirDefinition).content()
    addChild(name, dirDefinition)
  }

  inline fun zip(name: String, content: DirectoryContentBuilder.() -> Unit) {
    val zipDefinition = ZipSpec()
    DirectoryContentBuilderImpl(zipDefinition).content()
    addChild(name, zipDefinition)
  }

  /**
   * This method isn't supposed to be called directly, use other methods instead.
   */
  abstract fun addChild(name: String, spec: DirectoryContentSpecImpl)
}

interface DirectoryContentSpec {
  /**
   * Generates files, directories and archives accordingly to this specification in [target] directory
   */
  fun generate(target: File)

  /**
   * Generates files, directories and archives accordingly to this specification in a temp directory and return that directory.
   */
  fun generateInTempDir(): File
}

/**
 * Checks that contents of the given directory matches [spec].
 */
@JvmOverloads
fun File.assertMatches(spec: DirectoryContentSpec, fileTextMatcher: FileTextMatcher = FileTextMatcher.exact()) {
  assertDirectoryContentMatches(this, spec as DirectoryContentSpecImpl, "", fileTextMatcher)
}

interface FileTextMatcher {
  companion object {
    @JvmStatic
    fun ignoreBlankLines(): FileTextMatcher = FileTextMatchers.ignoreBlankLines
    @JvmStatic
    fun exact(): FileTextMatcher = FileTextMatchers.exact
  }
  fun matches(actualText: String, expectedText: String): Boolean
}

fun DirectoryContentSpec.generateInVirtualTempDir(): VirtualFile {
  val ioFile = generateInTempDir()
  val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile)!!
  UsefulTestCase.refreshRecursively(virtualFile)
  return virtualFile
}