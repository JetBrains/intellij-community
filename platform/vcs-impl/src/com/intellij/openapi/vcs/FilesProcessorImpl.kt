// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
abstract class FilesProcessorImpl(protected val project: Project, parentDisposable: Disposable) : FilesProcessor {
  private val files = mutableSetOf<VirtualFile>()

  abstract fun doActionOnChosenFiles(files: Collection<VirtualFile>)

  abstract fun doFilterFiles(files: Collection<VirtualFile>): Collection<VirtualFile>

  init {
    Disposer.register(parentDisposable, this)
  }

  override fun processFiles(files: Collection<VirtualFile>) {
    val filteredFiles = doFilterFiles(files)

    if (filteredFiles.isEmpty()) return

    addNewFiles(filteredFiles)

    if (needDoForCurrentProject()) {
      doActionOnChosenFiles(acquireValidFiles())
    }
    else {
      handleProcessingForCurrentProject()
    }
  }

  protected open fun handleProcessingForCurrentProject() = Unit

  protected fun removeFiles(filesToRemove: Collection<VirtualFile>): Boolean {
    synchronized(files) {
      return VcsUtil.removeAllFromSet(files, filesToRemove)
    }
  }

  protected fun isFilesEmpty(): Boolean {
    synchronized(files) {
      return files.isEmpty()
    }
  }

  private fun addNewFiles(filesToAdd: Collection<VirtualFile>) {
    synchronized(files) {
      files.addAll(filesToAdd)
    }
  }

  protected fun selectValidFiles(): List<VirtualFile> {
    synchronized(files) {
      files.removeAll { !it.isValid }
      return files.toList()
    }
  }

  protected fun acquireValidFiles(): List<VirtualFile> {
    synchronized(files) {
      val result = files.filter { it.isValid }
      files.clear()
      return result
    }
  }

  protected fun clearFiles() {
    synchronized(files) {
      files.clear()
    }
  }

  override fun dispose() {
    clearFiles()
  }

  protected abstract fun needDoForCurrentProject(): Boolean

  @TestOnly
  protected open fun waitForEventsProcessedInTestMode(){
    assert(ApplicationManager.getApplication().isUnitTestMode)
  }
}
