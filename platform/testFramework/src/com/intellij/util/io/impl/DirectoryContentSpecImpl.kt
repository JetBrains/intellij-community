// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.impl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.util.io.*
import org.junit.ComparisonFailure
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.Deflater
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readBytes

sealed class DirectoryContentSpecImpl : DirectoryContentSpec {
  /**
   * Path to the original file from which this spec was built. Will be used in 'Comparison Failure' dialog to apply changes to that file.
   */
  abstract val originalFile: Path?

  abstract override fun mergeWith(other: DirectoryContentSpec): DirectoryContentSpecImpl
}

sealed class DirectorySpecBase(override val originalFile: Path?) : DirectoryContentSpecImpl() {

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
      is JarSpec -> JarSpec()
    }
    result.children.putAll(children)
    for ((name, child) in other.children) {
      val oldChild = children[name]
      result.children[name] = oldChild?.mergeWith(child) ?: child
    }
    return result
  }
}

class DirectorySpec(originalFile: Path? = null) : DirectorySpecBase(originalFile) {
  override fun generate(target: File) {
    if (!FileUtil.createDirectory(target)) {
      throw IOException("Cannot create directory $target")
    }
    generateInDirectory(target)
  }
}

sealed class ZipSpecBase(private val extension: String) : DirectorySpecBase(null) {
  override fun generate(target: File) {
    val contentDir = FileUtil.createTempDirectory("$extension-content", null, false)
    try {
      generateInDirectory(contentDir)
      FileUtil.createParentDirs(target)
      compress(contentDir, target)
    }
    finally {
      FileUtil.delete(contentDir)
    }
  }

  abstract fun compress(contentDir: File, target: File)

  override fun generateInTempDir(): Path {
    val target = FileUtil.createTempFile("$extension-by-spec", ".$extension", true)
    generate(target)
    return target.toPath()
  }
}

class ZipSpec(val level: Int = Deflater.DEFAULT_COMPRESSION) : ZipSpecBase("zip") {
  override fun compress(contentDir: File, target: File) {
    Compressor.Zip(target).withLevel(level).use { it.addDirectory(contentDir) }
  }
}

class JarSpec : ZipSpecBase("jar") {
  override fun compress(contentDir: File, target: File) {
    Compressor.Jar(target).use {
      val manifestFile = File(contentDir, JarFile.MANIFEST_NAME)
      if (manifestFile.exists()) {
        val manifest = manifestFile.inputStream().use { Manifest(it) }
        it.addManifest(manifest)
        it.filter { path, _ -> path != JarFile.MANIFEST_NAME }
      }
      it.addDirectory(contentDir) 
    }
  }
}

class FileSpec(val content: ByteArray?, override val originalFile: Path? = null) : DirectoryContentSpecImpl() {
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

private fun DirectorySpecBase.toString(filePathFilter: (String) -> Boolean): String =
  ArrayList<String>().also { appendToString(this, it, 0, ".", filePathFilter) }.joinToString("\n")

private fun appendToString(spec: DirectorySpecBase, result: MutableList<String>, indent: Int,
                           relativePath: String, filePathFilter: (String) -> Boolean) {
  spec.getChildren().entries
    .filter { it.value !is FileSpec || filePathFilter("$relativePath/${it.key}") }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key })
    .forEach {
      result.add("${" ".repeat(indent)}${it.key}")
      val child = it.value
      if (child is DirectorySpec) {
        appendToString(child, result, indent + 2, "$relativePath/${it.key}", filePathFilter)
      }
    }
}

internal fun assertContentUnderFileMatches(file: Path,
                                           spec: DirectoryContentSpecImpl,
                                           fileTextMatcher: FileTextMatcher,
                                           filePathFilter: (String) -> Boolean,
                                           customErrorReporter: ContentMismatchReporter?,
                                           expectedDataIsInSpec: Boolean) {
  val errorReporter = customErrorReporter ?: ContentMismatchReporter { _, error -> throw error }
  if (spec is DirectorySpecBase) {
    val actualSpec = createSpecByPath(file, file)
    if (actualSpec is DirectorySpecBase) {
      val specString = spec.toString(filePathFilter)
      val dirString = actualSpec.toString(filePathFilter)
      val (expected, actual) = if (expectedDataIsInSpec) specString to dirString else dirString to specString
      if (actual != expected) {
        val message = "Expected equal strings: expected <$expected>, but got: <${actual}>"
        errorReporter.reportError(".", FileComparisonFailedError(message, expected, actual))
      }
    }
  }
  assertDirectoryContentMatches(file, spec, ".", fileTextMatcher, filePathFilter, errorReporter, expectedDataIsInSpec)
}

private fun ContentMismatchReporter.assertTrue(relativePath: String, errorMessage: String, condition: Boolean) {
  if (!condition) {
    reportError(relativePath, AssertionError(errorMessage))
  }
}

private fun assertDirectoryContentMatches(file: Path,
                                          spec: DirectoryContentSpecImpl,
                                          relativePath: String,
                                          fileTextMatcher: FileTextMatcher,
                                          filePathFilter: (String) -> Boolean,
                                          errorReporter: ContentMismatchReporter,
                                          expectedDataIsInSpec: Boolean) {
  errorReporter.assertTrue(relativePath, "$file doesn't exist", file.exists())
  when (spec) {
    is DirectorySpec -> {
      assertDirectoryMatches(file, spec, relativePath, fileTextMatcher, filePathFilter, errorReporter, expectedDataIsInSpec)
    }
    is ZipSpecBase -> {
      errorReporter.assertTrue(relativePath, "$file is not a file", file.isRegularFile())
      val dirForExtracted = FileUtil.createTempDirectory("extracted-${file.name}", null, false).toPath()
      ZipUtil.extract(file, dirForExtracted, null)
      assertDirectoryMatches(dirForExtracted, spec, relativePath, fileTextMatcher, filePathFilter, errorReporter, expectedDataIsInSpec)
      FileUtil.delete(dirForExtracted)
    }
    is FileSpec -> {
      errorReporter.assertTrue(relativePath, "$file is not a file", file.isRegularFile())
      if (spec.content != null) {
        val fileBytes = file.readBytes()
        if (!fileBytes.contentEquals(spec.content)) {
          val fileString = fileBytes.convertToText()
          val specString = spec.content.convertToText()
          val place = if (relativePath != ".") " at $relativePath" else ""
          if (fileString != null && specString != null) {
            if (!fileTextMatcher.matches(fileString, specString)) {
              val specFilePath = spec.originalFile?.toFile()?.absolutePath
              val (expected, actual) = if (expectedDataIsInSpec) specString to fileString else fileString to specString
              val (expectedPath, actualPath) = if (expectedDataIsInSpec) specFilePath to null else null to specFilePath
              val message = if (StringUtil.convertLineSeparators(fileString) != StringUtil.convertLineSeparators(specString)) {
                "File content mismatch$place: expected:\n $fileString\n but was\n$specString"
              }
              else {
                "Different line separators$place, expected ${StringUtil.detectSeparators(specString)}, but ${StringUtil.detectSeparators(fileString)} found:"
              }
              errorReporter.reportError(relativePath,
                                        FileComparisonFailedError(message, expected, actual, expectedPath, actualPath))
            }
          }
          else {
            errorReporter.reportError(relativePath, AssertionError("Binary file content mismatch$place"))
          }
        }
      }
    }
  }
}

private fun ByteArray.convertToText(): String? {
  if (isEmpty()) return ""
  val charset = when (CharsetToolkit(this, Charsets.UTF_8, false).guessFromContent(size)) {
    CharsetToolkit.GuessedEncoding.SEVEN_BIT -> Charsets.US_ASCII
    CharsetToolkit.GuessedEncoding.VALID_UTF8 -> Charsets.UTF_8
    else -> return null
  }
  return String(this, charset)
}

private fun assertDirectoryMatches(file: Path,
                                   spec: DirectorySpecBase,
                                   relativePath: String,
                                   fileTextMatcher: FileTextMatcher,
                                   filePathFilter: (String) -> Boolean,
                                   errorReporter: ContentMismatchReporter,
                                   expectedDataIsInSpec: Boolean) {
  errorReporter.assertTrue(relativePath, "$file is not a directory", file.isDirectory())
  fun childNameFilter(name: String) = filePathFilter("$relativePath/$name")
  val childrenNamesInDir = file.directoryStreamIfExists { children ->
    children.filter { it.isDirectory() || childNameFilter(it.name) }
      .map { it.name }.sortedWith(String.CASE_INSENSITIVE_ORDER)
  } ?: emptyList()
  val children = spec.getChildren()
  val childrenNamesInSpec = children.entries.filter { it.value is DirectorySpec || childNameFilter(it.key) }
    .map { it.key }.sortedWith(String.CASE_INSENSITIVE_ORDER)
  val specString = childrenNamesInSpec.joinToString("\n")
  val dirString = childrenNamesInDir.joinToString("\n")
  if (specString != dirString) {
    val (expected, actual) = if (expectedDataIsInSpec) specString to dirString else dirString to specString
    errorReporter.reportError(relativePath, ComparisonFailure("Directory content mismatch${if (relativePath != "") " at $relativePath" else ""}:",
                                    expected, actual))
  }
  for (child in childrenNamesInDir) {
    assertDirectoryContentMatches(file.resolve(child), children.getValue(child), "$relativePath/$child", fileTextMatcher, filePathFilter,
      errorReporter, expectedDataIsInSpec)
  }
}

internal fun fillSpecFromDirectory(spec: DirectorySpecBase, dir: Path, originalDir: Path?) {
  dir.directoryStreamIfExists { children ->
    children.forEach {
      spec.addChild(it.fileName.toString(), createSpecByPath(it, originalDir?.resolve(it.fileName)))
    }
  }
}

private fun createSpecByPath(path: Path, originalFile: Path?): DirectoryContentSpecImpl {
  if (path.isDirectory()) {
    return DirectorySpec(originalFile).also { fillSpecFromDirectory(it, path, originalFile) }
  }
  if (path.extension in setOf("zip", "jar")) {
    val dirForExtracted = FileUtil.createTempDirectory("extracted-${path.name}", null, false).toPath()
    ZipUtil.extract(path, dirForExtracted, null)
    val spec = if (path.extension == "jar") JarSpec() else ZipSpec()
    fillSpecFromDirectory(spec, dirForExtracted, null)
    FileUtil.delete(dirForExtracted)
    return spec
  }
  return FileSpec(Files.readAllBytes(path), originalFile)
}
