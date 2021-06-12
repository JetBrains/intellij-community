// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl.indexing

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ExtensionTestUtil.maskExtensions
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.assertions.Assertions
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexEx
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.util.indexing.roots.IndexableFilesDeduplicateFilter
import junit.framework.TestCase
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.rules.TestName
import kotlin.test.assertEquals

@RunsInEdt
abstract class IndexableFilesBaseTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val edtRule = EdtRule()

  @Rule
  @JvmField
  val projectModelRule = ProjectModelRule()

  @Rule
  @JvmField
  val disposableRule = DisposableRule()

  @Rule
  @JvmField
  val tempDirectory = TempDirectory()

  @Rule
  @JvmField
  val testName = TestName()

  val project: Project get() = projectModelRule.project

  @Before
  fun setUp() {
    runWriteAction {
      (IndexableSetContributor.EP_NAME.point as ExtensionPointImpl<*>).unregisterExtensions({ _, _ -> false }, false)
      (AdditionalLibraryRootsProvider.EP_NAME.point as ExtensionPointImpl<*>).unregisterExtensions({ _, _ -> false }, false)
    }
  }

  protected fun assertIndexableFiles(vararg expectedFiles: VirtualFile) {
    assertIndexableFiles(expectedNumberOfSkippedFiles = 0, expectedFiles = expectedFiles)
  }

  protected fun assertIndexableFiles(expectedNumberOfSkippedFiles: Int, vararg expectedFiles: VirtualFile) {
    val actualIndexed = hashSetOf<VirtualFile>()
    val collector = { fileOrDir: VirtualFile ->
      if (!actualIndexed.add(fileOrDir)) {
        TestCase.fail("$fileOrDir is scheduled for indexing twice")
      }
      true
    }
    iterateIndexableFiles(collector, project, expectedNumberOfSkippedFiles)
    val actualFiles = actualIndexed.filter { !it.isDirectory || it.`is`(VFileProperty.SYMLINK) }
    if (expectedFiles.isEmpty()) {
      Assertions.assertThat(actualFiles).overridingErrorMessage { actualFiles.joinToString { it.url + "\n" } }.isEmpty()
    }
    else {
      Assertions.assertThat(actualFiles).containsExactlyInAnyOrderElementsOf(expectedFiles.toList())
    }
  }

  private fun iterateIndexableFiles(processor: (VirtualFile) -> Boolean, project: Project, expectedNumberOfSkippedFiles: Int) {
    val fileBasedIndexEx = FileBasedIndex.getInstance() as FileBasedIndexEx
    val providers = fileBasedIndexEx.getOrderedIndexableFilesProviders (project)
    val indexableFilesDeduplicateFilter = IndexableFilesDeduplicateFilter.create()
    for (provider in providers) {
      provider.iterateFiles(project, processor, indexableFilesDeduplicateFilter)
    }
    assertEquals(expectedNumberOfSkippedFiles, indexableFilesDeduplicateFilter.numberOfSkippedFiles)
  }

  protected fun maskIndexableSetContributors(vararg indexableSetContributor: IndexableSetContributor) {
    runWriteAction {
      maskExtensions(IndexableSetContributor.EP_NAME, indexableSetContributor.toList(), disposableRule.disposable)
      fireRootsChanged()
    }
  }

  protected fun maskAdditionalLibraryRootsProviders(vararg additionalLibraryRootsProvider: AdditionalLibraryRootsProvider) {
    runWriteAction {
      maskExtensions(AdditionalLibraryRootsProvider.EP_NAME, additionalLibraryRootsProvider.toList(), disposableRule.disposable)
      fireRootsChanged()
    }
  }

  protected fun fireRootsChanged() {
    ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), false, true)
  }

  protected val ContentSpec.file: VirtualFile get() = resolveVirtualFile()

  protected infix operator fun VirtualFile.div(relativePath: String): VirtualFile {
    val child = findChild(relativePath)
    checkNotNull(child) { "Cannot resolve $relativePath against ${this.presentableUrl}" }
    child.refresh(false, true)
    return child
  }
}