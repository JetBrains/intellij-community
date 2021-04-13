// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil

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

    doProcess()
  }

  protected open fun doProcess(): Boolean {
    val doForCurrentProject = needDoForCurrentProject()

    if (doForCurrentProject) {
      doActionOnChosenFiles(acquireValidFiles())
      clearFiles()
    }

    return doForCurrentProject
  }

  @Synchronized
  protected fun removeFiles(filesToRemove: Collection<VirtualFile>): Boolean {
    return VcsUtil.removeAllFromSet(files, filesToRemove)
  }

  @Synchronized
  protected fun isFilesEmpty() = files.isEmpty()

  @Synchronized
  protected fun addNewFiles(filesToAdd: Collection<VirtualFile>) {
    files.addAll(filesToAdd)
  }

  @Synchronized
  protected fun acquireValidFiles(): List<VirtualFile> {
    files.removeAll { !it.isValid }
    return files.toList()
  }

  @Synchronized
  protected fun clearFiles() {
    files.clear()
  }

  override fun dispose() {
    clearFiles()
  }

  protected abstract fun needDoForCurrentProject(): Boolean
}
