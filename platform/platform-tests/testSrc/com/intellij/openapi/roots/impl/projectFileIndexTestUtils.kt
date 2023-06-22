// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndex
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.UsefulTestCase
import org.intellij.lang.annotations.MagicConstant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.fail

object ProjectFileIndexScopes {
  const val NOT_IN_PROJECT = 0
  const val IN_CONTENT = 1 shl 0 
  const val IN_LIBRARY = 1 shl 1
  const val EXCLUDED = 1 shl 2
  const val IN_SOURCE = 1 shl 3
  const val IN_TEST_SOURCE = 1 shl 4
  const val UNDER_IGNORED = 1 shl 5
  private const val IN_LIBRARY_SOURCE_AND_CLASSES_FLAG = 1 shl 6
  private const val IN_MODULE_SOURCE_BUT_NOT_IN_LIBRARY_SOURCE_FLAG = 1 shl 7
  const val EXCLUDED_FROM_MODULE_ONLY = 1 shl 8
  const val IN_LIBRARY_SOURCE_ONLY = 1 shl 9
  const val IN_LIBRARY_SOURCE_AND_CLASSES = IN_LIBRARY or IN_SOURCE or IN_LIBRARY_SOURCE_AND_CLASSES_FLAG
  const val IN_MODULE_SOURCE_BUT_NOT_IN_LIBRARY_SOURCE = IN_CONTENT or IN_SOURCE or IN_LIBRARY or IN_MODULE_SOURCE_BUT_NOT_IN_LIBRARY_SOURCE_FLAG

  fun ProjectFileIndex.assertInModule(file: VirtualFile,
                                      module: Module,
                                      contentRoot: VirtualFile,
                                      @MagicConstant(flagsFromClass = ProjectFileIndexPerformanceTest::class) scope: Int = IN_CONTENT) {
    assertScope(file, scope, module, contentRoot)
  }
  
  fun ProjectFileIndex.assertScope(file: VirtualFile, @MagicConstant(flagsFromClass = ProjectFileIndexPerformanceTest::class) scope: Int) {
    assertScope(file, scope, null, null)
  }
  
  fun ProjectFileIndex.assertInUnloadedModule(file: VirtualFile, moduleName: String, contentRoot: VirtualFile) {
    assertScope(file, EXCLUDED, null, contentRoot)
    assertEquals(moduleName, getUnloadedModuleNameForFile(file))
  }
  
  private fun ProjectFileIndex.assertScope(file: VirtualFile, @MagicConstant(flagsFromClass = ProjectFileIndexPerformanceTest::class) scope: Int = IN_CONTENT,
                                           module: Module?, contentRoot: VirtualFile?) {
    val inContent = scope and IN_CONTENT != 0
    val inLibrary = scope and IN_LIBRARY != 0
    val inSource = scope and IN_SOURCE != 0
    val isExcluded = scope and EXCLUDED != 0
    val isIgnored = scope and UNDER_IGNORED != 0
    checkScope(inContent, isInContent(file), "content", file)
    checkScope(inSource, isInSource(file), "source", file)
    checkScope(inContent && inSource && scope and IN_LIBRARY_SOURCE_ONLY == 0, isInSourceContent(file), "source content", file)
    checkScope(scope and IN_TEST_SOURCE != 0, isInTestSourceContent(file), "test source", file)
    checkScope(inContent || inLibrary, isInProject(file), "project", file)
    checkScope(inContent || inLibrary || isExcluded, isInProjectOrExcluded(file), "project or excluded", file)
    val actualModule = getModuleForFile(file)
    val actualContentRoot = getContentRootForFile(file)
    if (isExcluded || scope and EXCLUDED_FROM_MODULE_ONLY != 0) {
      assertNull(actualModule, "getModuleForFile() must return null for excluded file ${file.presentableUrl}")
      assertNull(actualContentRoot, "getContentRootForFile() must return null for excluded file ${file.presentableUrl}")
    }
    else {
      assertEquals(module, actualModule, "Incorrect module for ${file.presentableUrl}")
      assertEquals(contentRoot, actualContentRoot, "Incorrect content root for ${file.presentableUrl}")
    }
    assertEquals(module, getModuleForFile(file, false), "Incorrect module for ${file.presentableUrl}")
    assertEquals(contentRoot, getContentRootForFile(file, false), "Incorrect content root for ${file.presentableUrl}")
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

internal fun assertIteratedContent(module: Module, mustContain: List<VirtualFile>?, mustNotContain: List<VirtualFile>?) {
  assertIteratedContent(ModuleRootManager.getInstance(module).fileIndex, mustContain, mustNotContain)
  assertIteratedContent(ProjectFileIndex.getInstance(module.project), mustContain, mustNotContain)
}

internal fun assertIteratedContent(project: Project, mustContain: List<VirtualFile>? = null, mustNotContain: List<VirtualFile>? = null) {
  assertIteratedContent(ProjectFileIndex.getInstance(project), mustContain, mustNotContain)
}

private fun assertIteratedContent(fileIndex: FileIndex, mustContain: List<VirtualFile>?, mustNotContain: List<VirtualFile>?) {
  val collected = HashSet<VirtualFile>()
  fileIndex.iterateContent { fileOrDir: VirtualFile ->
    if (!collected.add(fileOrDir)) {
      fail("$fileOrDir visited twice")
    }
    true
  }
  if (mustContain != null) UsefulTestCase.assertContainsElements(collected, mustContain)
  if (mustNotContain != null) UsefulTestCase.assertDoesntContain(collected, mustNotContain)
}

internal fun assertIteratedContent(fileIndex: FileIndex,
                          root: VirtualFile,
                          mustContain: List<VirtualFile>?,
                          mustNotContain: List<VirtualFile>?) {
  val collected = HashSet<VirtualFile>()
  fileIndex.iterateContentUnderDirectory(root) { fileOrDir: VirtualFile ->
    if (!collected.add(fileOrDir)) {
      fail("$fileOrDir visited twice")
    }
    true
  }
  if (mustContain != null) UsefulTestCase.assertContainsElements(collected, mustContain)
  if (mustNotContain != null) UsefulTestCase.assertDoesntContain(collected, mustNotContain)
}
