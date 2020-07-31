// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile

abstract class FilesProcessorImpl(protected val project: Project, parentDisposable: Disposable) : FilesProcessor {
  private val files = mutableSetOf<VirtualFile>()

  abstract val askedBeforeProperty: String

  abstract val doForCurrentProjectProperty: String?

  abstract fun doActionOnChosenFiles(files: Collection<VirtualFile>)

  abstract fun doFilterFiles(files: Collection<VirtualFile>): Collection<VirtualFile>

  abstract fun rememberForAllProjects()

  protected open fun rememberForCurrentProject() {
    setForCurrentProject(true)
  }

  init {
    Disposer.register(parentDisposable, this)
  }

  override fun processFiles(files: List<VirtualFile>): List<VirtualFile> {
    val filteredFiles = doFilterFiles(files)

    if (filteredFiles.isEmpty()) return files

    addNewFiles(filteredFiles)

    doProcess()

    return files - filteredFiles
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
  protected fun removeFiles(filesToRemove: Collection<VirtualFile>): Boolean = files.removeAll(filesToRemove)

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

  protected fun setForCurrentProject(value: Boolean) {
    doForCurrentProjectProperty?.let { PropertiesComponent.getInstance(project).setValue(it, value) }
  }

  private fun getForCurrentProject(): Boolean =
    doForCurrentProjectProperty?.let { PropertiesComponent.getInstance(project).getBoolean(it, false) } ?: false

  protected fun notAskedBefore() = !wasAskedBefore()
  protected fun wasAskedBefore() = PropertiesComponent.getInstance(project).getBoolean(askedBeforeProperty, false)

  protected open fun needDoForCurrentProject() = getForCurrentProject()
}
