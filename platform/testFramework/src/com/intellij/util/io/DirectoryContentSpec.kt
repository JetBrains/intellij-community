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
import com.intellij.util.io.impl.*
import java.io.File

/**
 * Builds a data structure specifying content (files, their content, sub-directories, archives) of a directory. It can be used to either check
 * that a given directory matches this specification or to generate files in a directory accordingly to the specification.
 *
 * @author nik
 */
inline fun directoryContent(content: DirectoryContentBuilder.() -> Unit): DirectoryContentSpec {
  val builder = DirectoryContentBuilderImpl(DirectorySpec())
  builder.content()
  return builder.result
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
fun File.assertMatches(spec: DirectoryContentSpec) {
  assertDirectoryContentMatches(this, spec as DirectoryContentSpecImpl, "")
}

fun DirectoryContentSpec.generateInVirtualTempDir(): VirtualFile {
  val ioFile = generateInTempDir()
  val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile)!!
  virtualFile.refresh(false, true)
  return virtualFile
}