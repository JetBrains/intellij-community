// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.changes.CompositeFilePathHolder.IgnoredFilesCompositeHolder
import com.intellij.openapi.vcs.changes.CompositeFilePathHolder.UnversionedFilesCompositeHolder

internal class FileHolderComposite private constructor(
  private val project: Project,
  val unversionedFileHolder: UnversionedFilesCompositeHolder = UnversionedFilesCompositeHolder(project),
  val ignoredFileHolder: IgnoredFilesCompositeHolder = IgnoredFilesCompositeHolder(project),
  val modifiedWithoutEditingFileHolder: VirtualFileHolder = VirtualFileHolder(project),
  val lockedFileHolder: VirtualFileHolder = VirtualFileHolder(project),
  val logicallyLockedFileHolder: LogicallyLockedHolder = LogicallyLockedHolder(project),
  val rootSwitchFileHolder: SwitchedFileHolder = SwitchedFileHolder(project),
  val switchedFileHolder: SwitchedFileHolder = SwitchedFileHolder(project),
  val deletedFileHolder: DeletedFilesHolder = DeletedFilesHolder()
) : FileHolder {

  private val fileHolders
    get() = listOf(unversionedFileHolder, ignoredFileHolder, modifiedWithoutEditingFileHolder, lockedFileHolder, logicallyLockedFileHolder,
                   rootSwitchFileHolder, switchedFileHolder, deletedFileHolder)

  override fun cleanAll() = fileHolders.forEach { it.cleanAll() }
  override fun cleanUnderScope(scope: VcsDirtyScope) = fileHolders.forEach { it.cleanUnderScope(scope) }

  override fun copy(): FileHolderComposite =
    FileHolderComposite(project, unversionedFileHolder.copy(), ignoredFileHolder.copy(), modifiedWithoutEditingFileHolder.copy(),
                        lockedFileHolder.copy(), logicallyLockedFileHolder.copy(), rootSwitchFileHolder.copy(), switchedFileHolder.copy(),
                        deletedFileHolder.copy())

  override fun notifyVcsStarted(vcs: AbstractVcs) = fileHolders.forEach { it.notifyVcsStarted(vcs) }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is FileHolderComposite) return false

    return fileHolders == other.fileHolders
  }

  override fun hashCode(): Int = fileHolders.hashCode()

  companion object {
    @JvmStatic
    fun create(project: Project): FileHolderComposite = FileHolderComposite(project)
  }
}