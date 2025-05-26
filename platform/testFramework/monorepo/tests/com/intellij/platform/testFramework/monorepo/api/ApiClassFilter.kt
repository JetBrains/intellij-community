// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.monorepo.api

import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.tools.apiDump.packageName
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.inputStream

internal fun Path.apiClassFilter(): FileApiClassFilter? {
  if (exists()) {
    return FileApiClassFilter(this, inputStream().readApiEntries())
  }
  else {
    return null
  }
}

/**
 * @return list of non-blank lines which don't start with `#`
 */
private fun InputStream.readApiEntries(): List<String> {
  return bufferedReader().useLines {
    it.filter { line ->
      !line.isBlank() && !line.startsWith("#")
    }.toList()
  }
}

internal interface ApiClassFilter {

  operator fun contains(className: String): Boolean

  fun andThen(another: ApiClassFilter): ApiClassFilter {
    if (another == Empty) {
      return this
    }
    return object : ApiClassFilter {
      override fun contains(className: String): Boolean = className in this@ApiClassFilter || className in another
      override fun toString(): String = "${this@ApiClassFilter} | $another"
    }
  }

  data object Empty : ApiClassFilter {
    override fun contains(className: String): Boolean = false
    override fun andThen(another: ApiClassFilter): ApiClassFilter = another
  }
}

internal class FileApiClassFilter(
  private val path: Path,
  private val entries: List<String>,
) : ApiClassFilter {

  private val packages: Set<String>
  private val recursivePackages: Set<String> // TODO use prefix tree
  private val classes: Set<String>

  init {
    val packages = HashSet<String>()
    val recursivePackages = HashSet<String>()
    val classes = HashSet<String>()
    for (line in entries) {
      if (line.endsWith("/*")) {
        packages.add(line.removeSuffix("*"))
      }
      else if (line.endsWith("/**")) {
        recursivePackages.add(line.removeSuffix("**"))
      }
      else {
        classes.add(line)
      }
    }
    this.packages = packages
    this.recursivePackages = recursivePackages
    this.classes = classes
  }

  private val usedEntries = HashSet<String>()

  override fun toString(): String = path.toString()

  override fun contains(className: String): Boolean {
    if (className in classes) {
      usedEntries.add(className)
      return true
    }
    val packageName = className.packageName() + "/"
    if (packageName in packages) {
      usedEntries.add(packageName)
      return true
    }
    for (recursivePackage in recursivePackages) {
      if (packageName.startsWith(recursivePackage)) {
        usedEntries.add(recursivePackage)
        return true
      }
    }
    return false
  }

  fun checkAllEntriesAreUsed() {
    val unusedEntries = unusedEntries()
    if (unusedEntries.isEmpty()) {
      return
    }
    val message = buildString {
      append("$path entries does not match any exposed API. ")
      appendLine("The following entries are no longer exposed and should be removed from $path:")
      for (it in unusedEntries) {
        appendLine(it)
      }
    }
    throw FileComparisonFailedError(
      message,
      expected = entries.joinToString("\n", postfix = "\n"),
      actual = (entries - unusedEntries).joinToString("\n", postfix = "\n"),
      expectedFilePath = path.toString(),
    )
  }

  private fun unusedEntries(): Set<String> {
    val result = TreeSet<String>()
    (packages - usedEntries).mapTo(result) { "$it*" }
    (recursivePackages - usedEntries).mapTo(result) { "$it**" }
    result.addAll(classes - usedEntries)
    return result
  }

  fun checkEntriesSortedAndUnique() {
    val sorted = entries.toSortedSet().toList()
    if (sorted == entries) {
      return
    }
    throw FileComparisonFailedError(
      message = "$path contents are not sorted",
      expected = sorted.joinToString(separator = "\n", postfix = "\n"),
      actual = entries.joinToString(separator = "\n", postfix = "\n"),
      actualFilePath = path.toString(),
    )
  }
}
