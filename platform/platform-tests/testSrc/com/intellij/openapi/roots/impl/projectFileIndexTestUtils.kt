// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.lang.annotations.MagicConstant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.fail

internal object ProjectFileIndexScopes {
  const val NOT_IN_PROJECT = 0
  const val IN_CONTENT = 1
  const val IN_LIBRARY = 2
  const val EXCLUDED = 4
  const val IN_SOURCE = 8
  const val IN_TEST_SOURCE = 16
  const val UNDER_IGNORED = 32
  private const val IN_LIBRARY_SOURCE_AND_CLASSES_FLAG = 64
  const val IN_LIBRARY_SOURCE_AND_CLASSES = IN_LIBRARY or IN_SOURCE or IN_LIBRARY_SOURCE_AND_CLASSES_FLAG
  private const val IN_MODULE_SOURCE_BUT_NOT_IN_LIBRARY_SOURCE_FLAG = 128
  const val IN_MODULE_SOURCE_BUT_NOT_IN_LIBRARY_SOURCE = IN_CONTENT or IN_SOURCE or IN_LIBRARY or IN_MODULE_SOURCE_BUT_NOT_IN_LIBRARY_SOURCE_FLAG

  fun ProjectFileIndex.assertScope(file: VirtualFile, @MagicConstant(flagsFromClass = ProjectFileIndexPerformanceTest::class) scope: Int,
                                   module: Module? = null) {
    val inContent = scope and IN_CONTENT != 0
    val inLibrary = scope and IN_LIBRARY != 0
    val inSource = scope and IN_SOURCE != 0
    val isExcluded = scope and EXCLUDED != 0
    val isIgnored = scope and UNDER_IGNORED != 0
    checkScope(inContent, isInContent(file), "content", file)
    checkScope(inSource, isInSource(file), "source", file)
    checkScope(inContent && inSource, isInSourceContent(file), "source content", file)
    checkScope(scope and IN_TEST_SOURCE != 0, isInTestSourceContent(file), "test source", file)
    checkScope(inContent || inLibrary, isInProject(file), "project", file)
    checkScope(inContent || inLibrary || isExcluded, isInProjectOrExcluded(file), "project or excluded", file)
    val actualModule = getModuleForFile(file)
    if (isExcluded) {
      assertNull(actualModule, "getModuleForFile() must return null for excluded file ${file.presentableUrl}")
    }
    else {
      assertEquals(module, actualModule, file.presentableUrl)
    }
    assertEquals(module, getModuleForFile(file, false), file.presentableUrl)
    checkScope(inLibrary, isInLibrary(file), "library", file)
    val inLibrarySource = inLibrary && inSource && scope and IN_MODULE_SOURCE_BUT_NOT_IN_LIBRARY_SOURCE_FLAG == 0
    checkScope(inLibrarySource, isInLibrarySource(file), "library source", file)
    checkScope(inLibrary && !inLibrarySource || scope and IN_LIBRARY_SOURCE_AND_CLASSES_FLAG != 0, isInLibraryClasses(file), "library classes", file)
    checkScope(isExcluded or isIgnored, isExcluded(file), "excluded", file)
    checkScope(isIgnored, isUnderIgnored(file), "ignored", file)
  }

  private fun checkScope(expected: Boolean, actual: Boolean, description: String, file: VirtualFile) {
    if (expected != actual) {
      fail("${file.presentableUrl} expected to be ${if (expected) "in" else "not in"} $description, but it is ${if (actual) "in" else "not in"} $description")
    }
  }
}