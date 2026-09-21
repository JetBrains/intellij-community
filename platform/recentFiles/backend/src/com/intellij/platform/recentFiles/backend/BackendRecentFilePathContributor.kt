// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.recentFiles.shared.RecentFilePresentationContributor
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.PlatformUtils
import com.intellij.util.Processor
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.indexing.ProcessorWithThrottledCancellationCheck
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.Path
import kotlin.io.path.pathString

private val IDENTICAL_NAMES_CACHE_KEY = Key.create<Boolean>("IDENTICAL_NAMES_CACHE_KEY")

/**
 * Supplies the path text of a recent file from the file name index. Only a process with a backend has that index.
 */
internal class BackendRecentFilePathContributor : RecentFilePresentationContributor {
  override fun getPathText(project: Project, file: VirtualFile): String? {
    val parentPath = file.parent?.path?.toNioPathOrNull() ?: return null
    if (parentPath.nameCount == 0 || !areThereFilesWithSameName(file, project)) return null

    val filePath = parentPath.pathString
    val projectPath = project.basePath?.let { FileUtil.toSystemDependentName(it) }
    if (projectPath != null && FileUtil.isAncestor(projectPath, filePath, true)) {
      val locationRelativeToProjectDir = FileUtil.getRelativePath(projectPath, filePath, File.separatorChar)
      return if (locationRelativeToProjectDir != null && Path(locationRelativeToProjectDir).nameCount != 0) locationRelativeToProjectDir
      else filePath
    }
    if (FileUtil.isAncestor(SystemProperties.getUserHome(), filePath, true)) {
      val locationRelativeToUserHome = FileUtil.getLocationRelativeToUserHome(filePath)
      return if (Path(locationRelativeToUserHome).nameCount != 0) locationRelativeToUserHome else filePath
    }
    return filePath
  }
}

@RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
private fun areThereFilesWithSameName(virtualFile: VirtualFile, project: Project): Boolean {
  if (DumbService.getInstance(project).isDumb) return false

  val alreadyComputedValue = virtualFile.getUserData(IDENTICAL_NAMES_CACHE_KEY)
  if (alreadyComputedValue != null) return alreadyComputedValue

  val searchScope =
    if (PlatformUtils.isRider()) GlobalSearchScope.allScope(project) else GlobalSearchScope.projectScope(project)
  val processor = StopOnTwoIdenticalNamesProcessor(virtualFile.name)
  val cancellationAwareProcessor = ProcessorWithThrottledCancellationCheck(processor)
  FilenameIndex.processFilesByName(virtualFile.name, true, searchScope, cancellationAwareProcessor)
  val moreThanOneOccurrence = processor.areThereMoreThanOneFile()
  virtualFile.putUserData(IDENTICAL_NAMES_CACHE_KEY, moreThanOneOccurrence)
  return moreThanOneOccurrence
}

private class StopOnTwoIdenticalNamesProcessor(private val searchedName: String) : Processor<VirtualFile> {
  private var fileNameCounter = AtomicInteger(0)

  fun areThereMoreThanOneFile(): Boolean {
    return fileNameCounter.get() > 1
  }

  override fun process(file: VirtualFile): Boolean {
    val name = file.name

    if (searchedName != name) return true

    val newCount = fileNameCounter.incrementAndGet()
    return newCount <= 1
  }
}
