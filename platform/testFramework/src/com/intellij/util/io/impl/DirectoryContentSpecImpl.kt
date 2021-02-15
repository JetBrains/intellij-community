// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.impl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.io.*
import org.junit.Assert.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.collections.LinkedHashMap

sealed class DirectoryContentSpecImpl : DirectoryContentSpec {
  abstract override fun mergeWith(other: DirectoryContentSpec): DirectoryContentSpecImpl
}

abstract class DirectorySpecBase : DirectoryContentSpecImpl() {
  protected val children: LinkedHashMap<String, DirectoryContentSpecImpl> = LinkedHashMap()

  fun addChild(name: String, spec: DirectoryContentSpecImpl) {
    if (name in children) {
      val existing = children[name]
      if (spec is DirectorySpecBase && existing is DirectorySpecBase) {
        existing.children += spec.children
        return
      }
      throw IllegalArgumentException("'$name' already exists")
    }
    children[name] = spec
  }

  protected fun generateInDirectory(target: File) {
    for ((name, child) in children) {
      child.generate(File(target, name))
    }
  }

  override fun generateInTempDir(): Path {
    val target = FileUtil.createTempDirectory("directory-by-spec", null, true)
    generate(target)
    return target.toPath()
  }

  fun getChildren() : Map<String, DirectoryContentSpecImpl> = Collections.unmodifiableMap(children)

  override fun mergeWith(other: DirectoryContentSpec): DirectoryContentSpecImpl {
    require(other.javaClass == javaClass)
    other as DirectorySpecBase
    val result = when (other) {
      is DirectorySpec -> DirectorySpec()
      is ZipSpec -> ZipSpec()
      else -> error(other)
    }
    result.children.putAll(children)
    for ((name, child) in other.children) {
      val oldChild = children[name]
      result.children[name] = oldChild?.mergeWith(child) ?: child
    }
    return result
  }
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
      Compressor.Zip(target).use { it.addDirectory(contentDir) }
    }
    finally {
      FileUtil.delete(contentDir)
    }
  }

  override fun generateInTempDir(): Path {
    val target = FileUtil.createTempFile("zip-by-spec", ".zip", true)
    generate(target)
    return target.toPath()
  }
}

class FileSpec(val content: ByteArray?) : DirectoryContentSpecImpl() {
  override fun generate(target: File) {
    FileUtil.writeToFile(target, content ?: ByteArray(0))
  }

  override fun generateInTempDir(): Path {
    val target = FileUtil.createTempFile("file-by-spec", null, true)
    generate(target)
    return target.toPath()
  }

  override fun mergeWith(other: DirectoryContentSpec): DirectoryContentSpecImpl {
    return other as DirectoryContentSpecImpl
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

fun assertDirectoryContentMatches(file: File,
                                  spec: DirectoryContentSpecImpl,
                                  relativePath: String,
                                  fileTextMatcher: FileTextMatcher,
                                  filePathFilter: (String) -> Boolean) {
  assertTrue("$file doesn't exist", file.exists())
  when (spec) {
    is DirectorySpec -> {
      assertDirectoryMatches(file, spec, relativePath, fileTextMatcher, filePathFilter)
    }
    is ZipSpec -> {
      assertTrue("$file is not a file", file.isFile)
      val dirForExtracted = FileUtil.createTempDirectory("extracted-${file.name}", null, false)
      ZipUtil.extract(file, dirForExtracted, null)
      assertDirectoryMatches(dirForExtracted, spec, relativePath, fileTextMatcher, filePathFilter)
      FileUtil.delete(dirForExtracted)
    }
    is FileSpec -> {
      assertTrue("$file is not a file", file.isFile)
      if (spec.content != null) {
        val actualBytes = FileUtil.loadFileBytes(file)
        if (!Arrays.equals(actualBytes, spec.content)) {
          val actualString = actualBytes.convertToText()
          val expectedString = spec.content.convertToText()
          val place = if (relativePath != ".") " at $relativePath" else ""
          if (actualString != null && expectedString != null) {
            if (!fileTextMatcher.matches(actualString, expectedString)) {
              assertEquals("File content mismatch$place:", expectedString, actualString)
            }
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
  val encoding = CharsetToolkit(this, Charsets.UTF_8, false).guessFromContent(size)
  val charset = when (encoding) {
    CharsetToolkit.GuessedEncoding.SEVEN_BIT -> Charsets.US_ASCII
    CharsetToolkit.GuessedEncoding.VALID_UTF8 -> Charsets.UTF_8
    else -> return null
  }
  return String(this, charset)
}

private fun assertDirectoryMatches(file: File,
                                   spec: DirectorySpecBase,
                                   relativePath: String,
                                   fileTextMatcher: FileTextMatcher,
                                   filePathFilter: (String) -> Boolean) {
  assertTrue("$file is not a directory", file.isDirectory)
  fun childNameFilter(name: String) = filePathFilter("$relativePath/$name")
  val actualChildrenNames = file.listFiles()!!.filter { it.isDirectory || childNameFilter(it.name) }
    .map { it.name }.sortedWith(String.CASE_INSENSITIVE_ORDER)
  val children = spec.getChildren()
  val expectedChildrenNames = children.entries.filter { it.value !is FileSpec || childNameFilter(it.key) }
    .map { it.key }.sortedWith(String.CASE_INSENSITIVE_ORDER)
  assertEquals("Directory content mismatch${if (relativePath != "") " at $relativePath" else ""}:",
               expectedChildrenNames.joinToString("\n"), actualChildrenNames.joinToString("\n"))
  for (child in actualChildrenNames) {
    assertDirectoryContentMatches(File(file, child), children.get(child)!!, "$relativePath/$child", fileTextMatcher, filePathFilter)
  }
}

internal fun createSpecByDirectory(dir: Path): DirectorySpec {
  val spec = DirectorySpec()
  dir.directoryStreamIfExists { children ->
    children.forEach {
      spec.addChild(it.fileName.toString(), createSpecByPath(it))
    }
  }
  return spec
}

private fun createSpecByPath(path: Path): DirectoryContentSpecImpl {
  if (path.isFile()) {
    return FileSpec(Files.readAllBytes(path))
  }
  //todo support zip files
  return createSpecByDirectory(path)
}
