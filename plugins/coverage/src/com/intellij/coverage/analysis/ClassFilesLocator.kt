// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis

import com.intellij.openapi.progress.ProgressIndicatorProvider
import org.jetbrains.annotations.ApiStatus
import java.io.Closeable
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import java.util.zip.ZipFile

@ApiStatus.Internal
object ClassFilesLocator {
  internal fun findClassFiles(outputRoot: Path, packages: List<PackageEntry>): ClassFilesResource {
    val filter = ClassFilesFilter(packages)
    return when {
      packages.isEmpty() -> EmptyClassFilesResource
      Files.isDirectory(outputRoot) -> DirectoryClassFilesResource(outputRoot, packages, filter)
      Files.isRegularFile(outputRoot) -> createArchiveClassFilesResource(outputRoot, filter)
      else -> EmptyClassFilesResource
    }
  }

  /**
   * Collects class files generated from the requested top-level classes in the given package.
   * Supports both directory output roots and archive output roots, such as jars.
   */
  @JvmStatic
  fun collectClassFiles(outputRoot: Path, packageVMName: String, topLevelClassNames: Set<String>): List<Path> {
    if (topLevelClassNames.isEmpty()) return emptyList()

    val packageName = AnalysisUtils.internalNameToFqn(packageVMName)
    return findClassFiles(outputRoot, listOf(PackageEntry(packageName, topLevelClassNames.toList()))).use { resource ->
      resource.asSequence().map(LocatedClassFile::path).toList()
    }
  }

  private fun createArchiveClassFilesResource(
    outputRoot: Path,
    filter: ClassFilesFilter,
  ): ClassFilesResource {
    return try {
      ArchiveClassFilesResource(outputRoot, filter, ZipFile(outputRoot.toString()))
    }
    catch (_: IOException) {
      EmptyClassFilesResource
    }
  }
}

/** A single-pass class-file iterator. Entries may load bytes until this resource is closed. */
internal interface ClassFilesResource : Iterator<LocatedClassFile>, Closeable {
  operator fun iterator(): Iterator<LocatedClassFile> = this
}

internal class LocatedClassFile(
  /** A regular file path or a logical `archive.jar!/entry.class` path. */
  val path: Path,
  val relativePath: String,
  val packageVMName: String,
  val simpleName: String,
  bytesLoader: () -> ByteArray?,
) {
  private val bytes by lazy(LazyThreadSafetyMode.NONE, bytesLoader)

  fun loadBytes(): ByteArray? = bytes
}

private class ClassFilesFilter(packages: List<PackageEntry>) {
  private val recursivePackages = ArrayList<String>()
  private val requestedTopLevelClassNames = HashSet<String>()

  init {
    for ((packageName, simpleClassNames) in packages) {
      val packageVMName = AnalysisUtils.fqnToInternalName(packageName)
      if (simpleClassNames == null) {
        recursivePackages.add(packageVMName)
      }
      else {
        for (simpleClassName in simpleClassNames) {
          requestedTopLevelClassNames.add(
            AnalysisUtils.internalNameToFqn(AnalysisUtils.buildVMName(packageVMName, simpleClassName))
          )
        }
      }
    }
  }

  fun accepts(packageVMName: String, simpleName: String): Boolean {
    if (recursivePackages.any { packageVMName.isInPackage(it) }) return true
    val classVMName = AnalysisUtils.buildVMName(packageVMName, simpleName)
    return AnalysisUtils.getSourceToplevelFQName(classVMName) in requestedTopLevelClassNames
  }

  private fun String.isInPackage(packageVMName: String): Boolean {
    return packageVMName.isEmpty() || this == packageVMName || startsWith("$packageVMName/")
  }
}

private abstract class AbstractClassFilesResource : ClassFilesResource {
  private var closed = false
  private var nextComputed = false
  private var nextFile: LocatedClassFile? = null

  final override fun hasNext(): Boolean {
    if (closed) return false
    if (!nextComputed) {
      nextFile = findNext()
      nextComputed = true
    }
    return nextFile != null
  }

  final override fun next(): LocatedClassFile {
    if (!hasNext()) throw NoSuchElementException()
    val result = nextFile ?: throw NoSuchElementException()
    nextFile = null
    nextComputed = false
    return result
  }

  final override fun close() {
    if (closed) return
    closed = true
    nextFile = null
    nextComputed = true
    closeResource()
  }

  protected abstract fun findNext(): LocatedClassFile?

  protected open fun closeResource() {}
}

private object EmptyClassFilesResource : AbstractClassFilesResource() {
  override fun findNext(): LocatedClassFile? = null
}

private class DirectoryClassFilesResource(
  private val root: Path,
  packages: List<PackageEntry>,
  private val filter: ClassFilesFilter,
) : AbstractClassFilesResource() {
  private val traversals = packages.iterator()
  private val visitedPaths = HashSet<String>()
  private var currentStream: Stream<Path>? = null
  private var currentIterator: Iterator<Path>? = null

  override fun findNext(): LocatedClassFile? {
    while (true) {
      ProgressIndicatorProvider.checkCanceled()
      val iterator = currentIterator
      if (iterator == null) {
        if (!openNextTraversal()) return null
        continue
      }
      val hasNext = try {
        iterator.hasNext()
      }
      catch (_: UncheckedIOException) {
        false
      }
      if (!hasNext) {
        closeCurrentStream()
        if (!openNextTraversal()) return null
        continue
      }

      val classFile = try {
        iterator.next()
      }
      catch (_: UncheckedIOException) {
        closeCurrentStream()
        continue
      }
      if (!AnalysisUtils.isClassFile(classFile)) continue

      val relativePath = root.relativize(classFile)
      val relativePathString = relativePath.joinToString("/")
      if (!visitedPaths.add(relativePathString)) continue

      val packageVMName = relativePath.parent?.joinToString("/").orEmpty()
      val simpleName = AnalysisUtils.getClassName(relativePath)
      if (!filter.accepts(packageVMName, simpleName)) continue

      return LocatedClassFile(classFile, relativePathString, packageVMName, simpleName) {
        AnalysisUtils.loadClassBytes(classFile)
      }
    }
  }

  private fun openNextTraversal(): Boolean {
    while (traversals.hasNext()) {
      val (packageName, simpleClassNames) = traversals.next()
      val packageRoot = AnalysisUtils.fqnToInternalName(packageName).takeIf(String::isNotEmpty)?.let(root::resolve) ?: root
      try {
        currentStream = if (simpleClassNames == null) Files.walk(packageRoot) else Files.list(packageRoot)
        currentIterator = currentStream?.iterator()
        return true
      }
      catch (_: IOException) {
      }
    }
    return false
  }

  override fun closeResource() {
    closeCurrentStream()
  }

  private fun closeCurrentStream() {
    currentIterator = null
    currentStream?.close()
    currentStream = null
  }
}

private class ArchiveClassFilesResource(
  private val root: Path,
  private val filter: ClassFilesFilter,
  private val zipFile: ZipFile,
) : AbstractClassFilesResource() {
  private val entries = zipFile.entries()

  override fun findNext(): LocatedClassFile? {
    while (entries.hasMoreElements()) {
      ProgressIndicatorProvider.checkCanceled()
      val entry = entries.nextElement()
      if (entry.isDirectory || !entry.name.endsWith(".class")) continue

      val slashIndex = entry.name.lastIndexOf('/')
      val packageVMName = if (slashIndex < 0) "" else entry.name.substring(0, slashIndex)
      val simpleName = entry.name.substring(slashIndex + 1, entry.name.length - ".class".length)
      if (!filter.accepts(packageVMName, simpleName)) continue

      val classFile = AnalysisUtils.toArchiveEntryPath(root, entry.name)
      return LocatedClassFile(classFile, entry.name, packageVMName, simpleName) {
        try {
          AnalysisUtils.loadClassBytes(zipFile, entry.name)
        }
        catch (_: IllegalStateException) {
          null
        }
      }
    }
    return null
  }

  override fun closeResource() {
    try {
      zipFile.close()
    }
    catch (_: IOException) {
    }
  }
}
