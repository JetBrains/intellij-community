// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.TestIndexingModeSupporter

open class IndexingModeCodeInsightTestFixture<T : CodeInsightTestFixture> protected constructor(
  protected val delegate: T, private val indexingMode: TestIndexingModeSupporter.IndexingMode
) : CodeInsightTestFixture by delegate {

  private var indexingModeShutdownToken: TestIndexingModeSupporter.IndexingMode.ShutdownToken? = null

  companion object {
    fun wrapFixture(delegate: CodeInsightTestFixture, indexingMode: TestIndexingModeSupporter.IndexingMode): CodeInsightTestFixture {
      return if (indexingMode === TestIndexingModeSupporter.IndexingMode.SMART) {
        delegate
      }
      else IndexingModeCodeInsightTestFixture(delegate, indexingMode)
    }
  }

  @Throws(Exception::class)
  override fun setUp() {
    delegate.setUp()
    indexingModeShutdownToken = indexingMode.setUpTest(project, testRootDisposable)
  }

  @Throws(Exception::class)
  override fun tearDown() {
    indexingModeShutdownToken?.let { indexingMode.tearDownTest(project, it) }
    delegate.tearDown()
  }

  protected fun ensureIndexingStatus() {
    indexingMode.ensureIndexingStatus(project)
  }

  override fun copyFileToProject(sourceFilePath: String): VirtualFile {
    val file = delegate.copyFileToProject(sourceFilePath)
    ensureIndexingStatus()
    return file
  }

  override fun copyFileToProject(sourceFilePath: String, targetPath: String): VirtualFile {
    val file = delegate.copyFileToProject(sourceFilePath, targetPath)
    ensureIndexingStatus()
    return file
  }

  override fun copyDirectoryToProject(sourceFilePath: String, targetPath: String): VirtualFile {
    val file = delegate.copyDirectoryToProject(sourceFilePath, targetPath)
    ensureIndexingStatus()
    return file
  }

  override fun configureByFile(filePath: String): PsiFile? {
    val file = delegate.configureByFile(filePath)
    ensureIndexingStatus()
    return file
  }

  override fun configureByFiles(vararg filePaths: String): Array<PsiFile?> {
    val files = delegate.configureByFiles(*filePaths)
    ensureIndexingStatus()
    return files
  }

  override fun configureByText(fileType: FileType, text: String): PsiFile? {
    val file = delegate.configureByText(fileType, text)
    ensureIndexingStatus()
    return file
  }

  override fun configureByText(fileName: String, text: String): PsiFile? {
    val file = delegate.configureByText(fileName, text)
    ensureIndexingStatus()
    return file
  }

  override fun configureFromTempProjectFile(filePath: String): PsiFile? {
    val file = delegate.configureFromTempProjectFile(filePath)
    ensureIndexingStatus()
    return file
  }

  override fun configureFromExistingVirtualFile(virtualFile: VirtualFile) {
    delegate.configureFromExistingVirtualFile(virtualFile)
    ensureIndexingStatus()
  }

  override fun addFileToProject(relativePath: String, fileText: String): PsiFile? {
    val file = delegate.addFileToProject(relativePath, fileText)
    ensureIndexingStatus()
    return file
  }

  override fun openFileInEditor(file: VirtualFile) {
    delegate.openFileInEditor(file)
    ensureIndexingStatus()
  }

  override fun saveText(file: VirtualFile, text: String) {
    delegate.saveText(file, text)
    ensureIndexingStatus()
  }

  override fun getProjectDisposable(): Disposable {
    return delegate.projectDisposable //default method of interface is not delegated by default
  }

  override fun getTestRootDisposable(): Disposable {
    return delegate.testRootDisposable //default method of interface is not delegated by default
  }

  override fun isOpenedInMyEditor(virtualFile: VirtualFile): Boolean {
    return delegate.isOpenedInMyEditor(virtualFile)
  }
}