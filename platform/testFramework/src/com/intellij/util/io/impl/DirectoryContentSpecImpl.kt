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
package com.intellij.util.io.impl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.io.DirectoryContentBuilder
import com.intellij.util.io.DirectoryContentSpec
import com.intellij.util.io.ZipUtil
import org.junit.Assert.*
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.util.*
import java.util.zip.ZipOutputStream

/**
 * @author nik
 */
sealed class DirectoryContentSpecImpl : DirectoryContentSpec {
}

abstract class DirectorySpecBase : DirectoryContentSpecImpl() {
  protected val children = LinkedHashMap<String, DirectoryContentSpecImpl>()

  fun addChild(name: String, spec: DirectoryContentSpecImpl) {
    if (name in children) {
      throw IllegalArgumentException("'$name' already exists")
    }
    children[name] = spec
  }

  protected fun generateInDirectory(target: File) {
    for ((name, child) in children) {
      child.generate(File(target, name))
    }
  }

  override fun generateInTempDir(): File {
    val target = FileUtil.createTempDirectory("directory-by-spec", null, true)
    generate(target)
    return target
  }

  fun getChildren() : Map<String, DirectoryContentSpecImpl> = Collections.unmodifiableMap(children)
}

class DirectorySpec : DirectorySpecBase() {
  override fun generate(target: File) {
    if (!FileUtil.createDirectory(target)) {
      throw IOException("Cannot create directory $target")
    }
    generateInDirectory(target)
  }
}

class ZipSpec : DirectorySpecBase() {
  override fun generate(target: File) {
    val contentDir = FileUtil.createTempDirectory("zip-content", null, false)
    try {
      generateInDirectory(contentDir)
      ZipOutputStream(BufferedOutputStream(target.outputStream())).use {
        ZipUtil.addDirToZipRecursively(it, null, contentDir, "", null, null)
      }
    }
    finally {
      FileUtil.delete(contentDir)
    }
  }
}

class FileSpec(val content: ByteArray?) : DirectoryContentSpecImpl() {
  override fun generate(target: File) {
    FileUtil.writeToFile(target, content ?: ByteArray(0))
  }

  override fun generateInTempDir(): File {
    val target = FileUtil.createTempFile("file-by-spec", null, true)
    generate(target)
    return target
  }
}

class DirectoryContentBuilderImpl(val result: DirectorySpecBase) : DirectoryContentBuilder() {
  override fun addChild(name: String, spec: DirectoryContentSpecImpl) {
    result.addChild(name, spec)
  }

  override fun file(name: String) {
    addChild(name, FileSpec(null))
  }

  override fun file(name: String, text: String) {
    file(name, text.toByteArray())
  }

  override fun file(name: String, content: ByteArray) {
    addChild(name, FileSpec(content))
  }
}

fun assertDirectoryContentMatches(file: File, spec: DirectoryContentSpecImpl, relativePath: String) {
  when (spec) {
    is DirectorySpec -> {
      assertDirectoryMatches(file, spec, relativePath)
    }
    is ZipSpec -> {
      val dirForExtracted = FileUtil.createTempDirectory("extracted-${file.name}", null, false)
      ZipUtil.extract(file, dirForExtracted, null)
      assertDirectoryMatches(dirForExtracted, spec, relativePath)
      FileUtil.delete(dirForExtracted)
    }
    is FileSpec -> {
      assertTrue("$file is not a file", file.isFile)
      if (spec.content != null) {
        val actualBytes = FileUtil.loadFileBytes(file)
        if (!Arrays.equals(actualBytes, spec.content)) {
          val actualString = actualBytes.convertToText()
          val expectedString = spec.content.convertToText()
          val place = if (relativePath != "") " at $relativePath" else ""
          if (actualString != null && expectedString != null) {
            assertEquals("File content mismatch$place:", expectedString, actualString)
          }
          else {
            fail("Binary file content mismatch$place")
          }
        }
      }
    }
  }
}

private fun ByteArray.convertToText(): String? {
  val encoding = CharsetToolkit(this, Charsets.UTF_8).guessFromContent(size)
  val charset = when (encoding) {
    CharsetToolkit.GuessedEncoding.SEVEN_BIT -> Charsets.US_ASCII
    CharsetToolkit.GuessedEncoding.VALID_UTF8 -> Charsets.UTF_8
    else -> return null
  }
  return String(this, charset)
}

private fun assertDirectoryMatches(file: File, spec: DirectorySpecBase, relativePath: String) {
  assertTrue("$file is not a directory", file.isDirectory)
  val actualChildrenNames = file.list().sortedWith(String.CASE_INSENSITIVE_ORDER)
  val children = spec.getChildren()
  val expectedChildrenNames = children.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
  assertEquals("Directory content mismatch${if (relativePath != "") " at $relativePath" else ""}:",
               expectedChildrenNames.joinToString("\n"), actualChildrenNames.joinToString("\n"))
  actualChildrenNames.forEach { child ->
    assertDirectoryContentMatches(File(file, child), children[child]!!, "$relativePath/$child")
  }
}
