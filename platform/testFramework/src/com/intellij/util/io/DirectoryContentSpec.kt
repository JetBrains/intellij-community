// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


package com.intellij.util.io

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.impl.*
import org.junit.rules.ErrorCollector
import java.io.File
import java.nio.file.Path
import java.util.zip.Deflater

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
 * @param originalDir point to an original directory located in the project sources from which [dir] was built; will be used to allow fixing
 * the original files directly from 'Comparison Failure' dialog
 */
@JvmOverloads
fun directoryContentOf(dir: Path, originalDir: Path = dir): DirectoryContentSpec {
  return DirectorySpec().also { fillSpecFromDirectory(it, dir, originalDir) }
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
    zip(name, Deflater.DEFAULT_COMPRESSION, content)
  }

  inline fun uncompressedZip(name: String, content: DirectoryContentBuilder.() -> Unit) {
    zip(name, Deflater.NO_COMPRESSION, content)
  }

  inline fun zip(name: String, compression: Int, content: DirectoryContentBuilder.() -> Unit) {
    val zipDefinition = ZipSpec(compression)
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
  fun generateInTempDir(): Path

  /**
   * Returns specification for a directory which contain all data from this instance and data from [other]. If the both instance have
   * specification for the same file, data from [other] wins.
   */
  fun mergeWith(other: DirectoryContentSpec): DirectoryContentSpec
}

/**
 * Checks that contents of the given directory matches [spec].
 * @param filePathFilter determines which relative paths should be checked
 * @param errorCollector will be used to report all errors at once if provided, otherwise only the first error will be reported
 */
@JvmOverloads
fun File.assertMatches(spec: DirectoryContentSpec, fileTextMatcher: FileTextMatcher = FileTextMatcher.exact(),
                       filePathFilter: (String) -> Boolean = { true }, errorCollector: ErrorCollector? = null) {
  assertContentUnderFileMatches(toPath(), spec as DirectoryContentSpecImpl, fileTextMatcher, filePathFilter, errorCollector.asReporter(),
                                expectedDataIsInSpec = true)
}

/**
 * Checks that contents of the given directory matches [spec].
 * @param filePathFilter determines which relative paths should be checked
 * @param errorCollector will be used to report all errors at once if provided, otherwise only the first error will be reported
 */
@JvmOverloads
fun Path.assertMatches(spec: DirectoryContentSpec, fileTextMatcher: FileTextMatcher = FileTextMatcher.exact(),
                       filePathFilter: (String) -> Boolean = { true }, errorCollector: ErrorCollector? = null) {
  assertContentUnderFileMatches(this, spec as DirectoryContentSpecImpl, fileTextMatcher, filePathFilter, errorCollector.asReporter(),
                                expectedDataIsInSpec = true)
}

/**
 * Checks that contents of [path] matches this spec. The same as [Path.assertMatches], but in case of comparison failure the data
 * from the spec will be shown as actual, and the data from the file will be shown as expected.
 */
@JvmOverloads
fun DirectoryContentSpec.assertIsMatchedBy(path: Path, fileTextMatcher: FileTextMatcher = FileTextMatcher.exact(),
                                           filePathFilter: (String) -> Boolean = { true }, errorCollector: ErrorCollector? = null) {
  assertContentUnderFileMatches(path, this as DirectoryContentSpecImpl, fileTextMatcher, filePathFilter, errorCollector.asReporter(),
                                expectedDataIsInSpec = false)
}

/**
 * Checks that contents of [path] matches this spec. The same as [Path.assertMatches], but in case of comparison failure the data
 * from the spec will be shown as actual, and the data from the file will be shown as expected.
 */
@JvmOverloads
fun DirectoryContentSpec.assertIsMatchedBy(path: Path, fileTextMatcher: FileTextMatcher = FileTextMatcher.exact(),
                                           filePathFilter: (String) -> Boolean, customErrorReporter: ContentMismatchReporter?) {
  assertContentUnderFileMatches(path, this as DirectoryContentSpecImpl, fileTextMatcher, filePathFilter, customErrorReporter, 
                                expectedDataIsInSpec = false)
}

private fun ErrorCollector?.asReporter(): ContentMismatchReporter? = this?.let { ContentMismatchReporter { _, error -> it.addError(error) } }

fun interface ContentMismatchReporter {
  fun reportError(relativePath: String, error: Throwable)
}

interface FileTextMatcher {
  companion object {
    @JvmStatic
    fun ignoreBlankLines(): FileTextMatcher = FileTextMatchers.ignoreBlankLines

    @JvmStatic
    fun exact(): FileTextMatcher = FileTextMatchers.exact

    @JvmStatic
    fun ignoreLineSeparators(): FileTextMatcher = FileTextMatchers.lines

    @JvmStatic
    fun ignoreXmlFormatting(): FileTextMatcher = FileTextMatchers.ignoreXmlFormatting
  }

  fun matches(actualText: String, expectedText: String): Boolean
}

fun DirectoryContentSpec.generateInVirtualTempDir(): VirtualFile {
  return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(generateInTempDir())!!
}

/**
 * Generates files, directories and archives accordingly to this specification in [target] directory and refresh them in VFS
 */
fun DirectoryContentSpec.generate(target: VirtualFile) {
  generate(VfsUtil.virtualToIoFile(target))
  VfsUtil.markDirtyAndRefresh(false, true, true, target)
}