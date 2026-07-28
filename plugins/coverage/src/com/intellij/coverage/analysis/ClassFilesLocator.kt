// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis

import com.intellij.openapi.progress.ProgressIndicatorProvider
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

@ApiStatus.Internal
object ClassFilesLocator {
  internal fun findClassFiles(
    outputRoot: Path,
    rootPackageVMName: String,
    packagePathInRoot: String,
    requestedSimpleNames: Set<String>?,
  ): List<LocatedClassFiles> {
    val source = createClassFilesSource(outputRoot) ?: return emptyList()
    val requestedTopLevelNames = requestedSimpleNames?.mapTo(HashSet()) { simpleName ->
      AnalysisUtils.internalNameToFqn(AnalysisUtils.buildVMName(rootPackageVMName, simpleName))
    }
    val context = ClassFilesSourceContext(rootPackageVMName, packagePathInRoot, requestedSimpleNames == null)
    val topLevelClasses = LinkedHashMap<LocatedClassKey, MutableList<Path>>()
    source.findClassFiles(context).forEach { (packageVMName, simpleName, classFile) ->
      val classVMName = AnalysisUtils.buildVMName(packageVMName, simpleName)
      val topLevelClassName = AnalysisUtils.getSourceToplevelFQName(classVMName)
      if (requestedTopLevelNames != null && topLevelClassName !in requestedTopLevelNames) return@forEach
      topLevelClasses.getOrPut(LocatedClassKey(topLevelClassName, packageVMName), ::ArrayList).add(classFile)
    }
    return topLevelClasses.map { (key, files) -> LocatedClassFiles(key.topLevelClassName, key.packageVMName, files) }
  }

  /**
   * Collects class files generated from the requested top-level classes in the given package.
   * Supports both directory output roots and archive output roots, such as jars.
   */
  @JvmStatic
  fun collectClassFiles(outputRoot: Path, packageVMName: String, topLevelClassNames: Set<String>): List<Path> {
    if (topLevelClassNames.isEmpty()) return emptyList()
    return findClassFiles(outputRoot, packageVMName, packageVMName, topLevelClassNames)
      .flatMap(LocatedClassFiles::files)
  }

  private fun createClassFilesSource(outputRoot: Path): ClassFilesSource? = when {
    Files.isDirectory(outputRoot) -> DirectoryClassFilesSource(outputRoot)
    Files.isRegularFile(outputRoot) -> ArchiveClassFilesSource(outputRoot)
    else -> null
  }
}

internal data class LocatedClassFiles(
  val topLevelClassName: String,
  val packageVMName: String,
  val files: List<Path>,
)

private data class LocatedClassKey(val topLevelClassName: String, val packageVMName: String)

private data class DiscoveredClassFile(val packageVMName: String, val simpleName: String, val path: Path)

private data class ClassFilesSourceContext(
  val rootPackageVMName: String,
  val packagePathInRoot: String,
  val includeSubpackages: Boolean,
)

private interface ClassFilesSource {
  fun findClassFiles(context: ClassFilesSourceContext): List<DiscoveredClassFile>
}

private class DirectoryClassFilesSource(private val outputRoot: Path) : ClassFilesSource {
  override fun findClassFiles(context: ClassFilesSourceContext): List<DiscoveredClassFile> {
    val packageRoot = context.packagePathInRoot.takeIf(String::isNotEmpty)?.let(outputRoot::resolve) ?: outputRoot
    if (!Files.exists(packageRoot)) return emptyList()

    val result = ArrayList<DiscoveredClassFile>()
    val stack = ArrayDeque<PackageData>()
    stack.addLast(PackageData(context.rootPackageVMName, listChildren(packageRoot)))
    while (stack.isNotEmpty()) {
      ProgressIndicatorProvider.checkCanceled()
      val (packageVMName, children) = stack.removeLast()
      for (child in children) {
        when {
          AnalysisUtils.isClassFile(child) -> result.add(DiscoveredClassFile(packageVMName, AnalysisUtils.getClassName(child), child))
          context.includeSubpackages && Files.isDirectory(child) -> {
            val childPackageVMName = AnalysisUtils.buildVMName(packageVMName, child.fileName.toString())
            stack.addLast(PackageData(childPackageVMName, listChildren(child)))
          }
        }
      }
    }
    return result
  }

  private fun listChildren(packageRoot: Path): List<Path> {
    return try {
      Files.list(packageRoot).use { it.toList() }
    }
    catch (_: IOException) {
      emptyList()
    }
  }

  private data class PackageData(val packageVMName: String, val children: List<Path>)
}

private class ArchiveClassFilesSource(private val outputRoot: Path) : ClassFilesSource {
  override fun findClassFiles(context: ClassFilesSourceContext): List<DiscoveredClassFile> {
    val prefix = context.packagePathInRoot.takeIf(String::isNotEmpty)?.plus('/') ?: ""
    return try {
      val result = ArrayList<DiscoveredClassFile>()
      ZipInputStream(Files.newInputStream(outputRoot)).use { input ->
        while (true) {
          ProgressIndicatorProvider.checkCanceled()
          val entry = input.nextEntry ?: break
          if (entry.isDirectory || !entry.name.endsWith(".class") || prefix.isNotEmpty() && !entry.name.startsWith(prefix)) continue

          val relativePath = entry.name.removePrefix(prefix)
          val slashIndex = relativePath.lastIndexOf('/')
          if (!context.includeSubpackages && slashIndex >= 0) continue

          val packageVMName = if (slashIndex < 0) context.rootPackageVMName
          else AnalysisUtils.buildVMName(context.rootPackageVMName, relativePath.substring(0, slashIndex))
          val simpleName = relativePath.substring(slashIndex + 1, relativePath.length - ".class".length)
          result.add(DiscoveredClassFile(packageVMName, simpleName, AnalysisUtils.toArchiveEntryPath(outputRoot, entry.name)))
        }
      }
      result
    }
    catch (_: IOException) {
      emptyList()
    }
  }
}
