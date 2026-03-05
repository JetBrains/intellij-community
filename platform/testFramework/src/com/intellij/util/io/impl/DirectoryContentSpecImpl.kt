// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.impl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.util.io.Compressor
import com.intellij.util.io.ContentMismatchReporter
import com.intellij.util.io.DirectoryContentBuilder
import com.intellij.util.io.DirectoryContentSpec
import com.intellij.util.io.FileTextMatcher
import com.intellij.util.io.ZipUtil
import com.intellij.util.io.createDirectories
import com.intellij.util.io.createParentDirectories
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.io.write
import org.junit.ComparisonFailure
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.Deflater
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name

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
        for (child in spec.children) {
          existing.addChild(child.key, child.value)
        }
        return
      }
      throw IllegalArgumentException("'$name' already exists")
    }
    children[name] = spec
  }

  protected fun generateInDirectory(target: Path) {
    for ((name, child) in children) {
      child.generate(path = target.resolve(name))
    }
  }

  override fun generateInTempDir(): Path {
    val target = FileUtilRt.createTempDirectory("directory-by-spec", null, true).toPath()
    generate(target)
    return target
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
  override fun generate(path: Path) {
    path.createDirectories()
    generateInDirectory(path)
  }
}

sealed class ZipSpecBase(private val extension: String) : DirectorySpecBase(null) {
  override fun generate(path: Path) {
    val contentDir = FileUtil.createTempDirectory("$extension-content", null, false)
    try {
      generateInDirectory(contentDir.toPath())
      path.createParentDirectories()
      compress(contentDir, path)
    }
    finally {
      FileUtil.delete(contentDir)
    }
  }

  abstract fun compress(contentDir: File, target: Path)

  override fun generateInTempDir(): Path {
    val target = FileUtil.createTempFile("$extension-by-spec", ".$extension", true).toPath()
    generate(target)
    return target
  }
}

class ZipSpec(val level: Int = Deflater.DEFAULT_COMPRESSION) : ZipSpecBase("zip") {
  override fun compress(contentDir: File, target: Path) {
    Compressor.Zip(target).withLevel(level).use { it.addDirectory(contentDir) }
  }
}

class JarSpec : ZipSpecBase("jar") {
  override fun compress(contentDir: File, target: Path) {
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
  init {
      if (content != null && originalFile == null) {
        println("hello")
      }
  }
  override fun generate(path: Path) {
    path.write(content ?: ByteArray(0))
  }

  override fun generateInTempDir(): Path {
    val target = FileUtil.createTempFile("file-by-spec", null, true).toPath()
    generate(target)
    return target
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

fun DirectorySpecBase.drop(dropEmptyDirectories: Boolean, filePathFilter: (String) -> Boolean): DirectorySpecBase =
  dropImpl(dropEmptyDirectories, filePathFilter, ".")

private fun DirectorySpecBase.dropImpl(
  dropEmptyDirectories: Boolean,
  filePathFilter: (String) -> Boolean,
  relativePath: String,
): DirectorySpecBase {
  val result: DirectorySpecBase = when (this) {
    is DirectorySpec -> DirectorySpec()
    is ZipSpec -> ZipSpec(level)
    is JarSpec -> JarSpec()
  }
  for ((name, child) in getChildren()) {
    val childPath = "$relativePath/$name"
    when (child) {
      is DirectorySpecBase -> {
        val filtered = child.dropImpl(dropEmptyDirectories, filePathFilter, childPath)
        if (!dropEmptyDirectories || filtered.getChildren().isNotEmpty()) {
          result.addChild(name, filtered)
        }
      }
      is FileSpec -> {
        if (filePathFilter(childPath)) {
          result.addChild(name, child)
        }
      }
    }
  }
  return result
}

private fun DirectorySpecBase.toStructureString(): String =
  ArrayList<String>().also { appendToString(this, it, 0) }.joinToString("\n")

private fun appendToString(spec: DirectorySpecBase, result: MutableList<String>, indent: Int) {
  spec.getChildren().entries
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key })
    .forEach { (name, child) ->
      result.add("${" ".repeat(indent)}$name")
      if (child is DirectorySpec) {
        appendToString(child, result, indent + 2)
      }
    }
}

internal fun assertContentUnderFileMatches(file: Path,
                                           spec: DirectoryContentSpecImpl,
                                           fileTextMatcher: FileTextMatcher,
                                           filePathFilter: (String) -> Boolean,
                                           ignoreEmptyDirectories: Boolean,
                                           customErrorReporter: ContentMismatchReporter?,
                                           expectedDataIsInSpec: Boolean) {
  val errorReporter = customErrorReporter ?: ContentMismatchReporter { _, error -> throw error }
  if (!file.exists()) {
    errorReporter.reportError(".", AssertionError("$file doesn't exist"))
    return
  }
  val filteredExpected = if (spec is DirectorySpecBase) spec.drop(dropEmptyDirectories = ignoreEmptyDirectories, filePathFilter) else spec
  val actualRaw = createSpecByPath(file, file)
  val filteredActual = if (actualRaw is DirectorySpecBase) actualRaw.drop(dropEmptyDirectories = ignoreEmptyDirectories, filePathFilter) else actualRaw
  if (filteredExpected is DirectorySpecBase && filteredActual is DirectorySpecBase) {
    val specString = filteredExpected.toStructureString()
    val dirString = filteredActual.toStructureString()
    val (expected, actual) = if (expectedDataIsInSpec) specString to dirString else dirString to specString
    if (actual != expected) {
      val message = "Expected equal strings: expected <$expected>, but got: <${actual}>"
      errorReporter.reportError(".", FileComparisonFailedError(message, expected, actual))
    }
  }
  if (expectedDataIsInSpec) {
    assertSpecsMatch(filteredActual, filteredExpected, ".", fileTextMatcher, errorReporter)
  } else {
    assertSpecsMatch(filteredExpected, filteredActual, ".", fileTextMatcher, errorReporter)
  }
}

private fun assertSpecsMatch(actual: DirectoryContentSpecImpl,
                             expected: DirectoryContentSpecImpl,
                             relativePath: String,
                             fileTextMatcher: FileTextMatcher,
                             errorReporter: ContentMismatchReporter) {
  when {
    expected is DirectorySpecBase && actual is DirectorySpecBase -> {
      assertDirectorySpecsMatch(actual, expected, relativePath, fileTextMatcher, errorReporter)
    }
    expected is FileSpec && actual is FileSpec -> {
      if (expected.content != null) {
        val fileBytes = actual.content ?: ByteArray(0)
        if (!fileBytes.contentEquals(expected.content)) {
          val fileString = fileBytes.convertToText()
          val specString = expected.content.convertToText()
          val place = if (relativePath != ".") " at $relativePath" else ""
          if (fileString != null && specString != null) {
            if (!fileTextMatcher.matches(fileString, specString)) {
              val specFilePath = expected.originalFile?.toFile()?.absolutePath
              val actualFilePath = actual.originalFile?.toFile()?.absolutePath
              val message = if (StringUtil.convertLineSeparators(fileString) != StringUtil.convertLineSeparators(specString)) {
                "File content mismatch$place: expected:\n $fileString\n but was\n$specString"
              }
              else {
                "Different line separators$place, expected ${StringUtil.detectSeparators(specString)}, but ${StringUtil.detectSeparators(fileString)} found:"
              }
              errorReporter.reportError(relativePath,
                                        FileComparisonFailedError(message, specString, fileString, specFilePath, actualFilePath))
            }
          }
          else {
            errorReporter.reportError(relativePath, AssertionError("Binary file content mismatch$place"))
          }
        }
      }
    }
    expected is ZipSpecBase && actual is FileSpec -> {
      val originalFile = actual.originalFile
      if (originalFile == null) {
        errorReporter.reportError(relativePath,
                                  AssertionError("Type mismatch at $relativePath: expected ${expected::class.simpleName} " +
                                                 "but got ${actual::class.simpleName} (no original file path available)"))
        return
      }
      assertDirectorySpecsMatch(extractZipSpec(originalFile), expected, relativePath, fileTextMatcher, errorReporter)
    }
    actual is ZipSpecBase && expected is FileSpec -> {
      val originalFile = expected.originalFile
      if (originalFile == null) {
        errorReporter.reportError(relativePath,
                                  AssertionError("Type mismatch at $relativePath: expected ${expected::class.simpleName} " +
                                                 "but got ${actual::class.simpleName} (no original file path available)"))
        return
      }
      assertDirectorySpecsMatch(actual, extractZipSpec(originalFile), relativePath, fileTextMatcher, errorReporter)
    }
    else -> {
      errorReporter.reportError(relativePath,
                                AssertionError("Type mismatch at $relativePath: expected ${expected::class.simpleName} but got ${actual::class.simpleName}"))
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

private fun assertDirectorySpecsMatch(actual: DirectorySpecBase,
                                      expected: DirectorySpecBase,
                                      relativePath: String,
                                      fileTextMatcher: FileTextMatcher,
                                      errorReporter: ContentMismatchReporter) {
  val expectedChildren = expected.getChildren()
  val actualChildren = actual.getChildren()
  val expectedNames = expectedChildren.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
  val actualNames = actualChildren.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
  val expectedNamesStr = expectedNames.joinToString("\n")
  val actualNamesStr = actualNames.joinToString("\n")
  if (expectedNamesStr != actualNamesStr) {
    errorReporter.reportError(relativePath, ComparisonFailure(
      "Directory content mismatch${if (relativePath != ".") " at $relativePath" else ""}:",
      expectedNamesStr, actualNamesStr
    ))
  }
  for (name in actualNames) {
    val actualChild = actualChildren[name] ?: continue
    val expectedChild = expectedChildren[name] ?: continue
    assertSpecsMatch(actualChild, expectedChild, "$relativePath/$name", fileTextMatcher, errorReporter)
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
  if (path.extension in setOf("zip", "jar", "war")) {
    return extractZipSpec(path)
  }
  return FileSpec(Files.readAllBytes(path), originalFile)
}

private fun extractZipSpec(path: Path): ZipSpecBase {
  val dirForExtracted = FileUtil.createTempDirectory("extracted-${path.name}", null, false).toPath()
  ZipUtil.extract(path, dirForExtracted, null)
  val spec = if (path.extension == "jar") JarSpec() else ZipSpec()
  fillSpecFromDirectory(spec, dirForExtracted, null)
  FileUtilRt.deleteRecursively(dirForExtracted)
  return spec
}
