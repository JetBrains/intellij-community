// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.changes.FileHolder.HolderType.*

internal class FileHolderComposite : FileHolder {
  private val fileHolders = mutableMapOf<FileHolder.HolderType, FileHolder>()

  val unversionedFileHolder: FilePathHolder get() = fileHolders[UNVERSIONED] as FilePathHolder
  val ignoredFileHolder: IgnoredFilesCompositeHolder get() = fileHolders[IGNORED] as IgnoredFilesCompositeHolder
  val modifiedWithoutEditingFileHolder: VirtualFileHolder get() = fileHolders[MODIFIED_WITHOUT_EDITING] as VirtualFileHolder
  val lockedFileHolder: VirtualFileHolder get() = fileHolders[LOCKED] as VirtualFileHolder
  val logicallyLockedFileHolder: LogicallyLockedHolder get() = fileHolders[LOGICALLY_LOCKED] as LogicallyLockedHolder
  val rootSwitchFileHolder: SwitchedFileHolder get() = fileHolders[ROOT_SWITCH] as SwitchedFileHolder
  val switchedFileHolder: SwitchedFileHolder get() = fileHolders[SWITCHED] as SwitchedFileHolder
  val deletedFileHolder: DeletedFilesHolder get() = fileHolders[DELETED] as DeletedFilesHolder

  constructor(project: Project) {
    add(FilePathHolder(project, UNVERSIONED))
    add(SwitchedFileHolder(project, ROOT_SWITCH))
    add(SwitchedFileHolder(project, SWITCHED))
    add(VirtualFileHolder(project, MODIFIED_WITHOUT_EDITING))
    add(IgnoredFilesCompositeHolder(project))
    add(VirtualFileHolder(project, LOCKED))
    add(LogicallyLockedHolder(project))
    add(DeletedFilesHolder())
  }

  private constructor(holder: FileHolderComposite) {
    holder.fileHolders.values.forEach { add(it.copy()) }
  }

  private fun add(fileHolder: FileHolder) {
    fileHolders[fileHolder.type] = fileHolder
  }

  override fun cleanAll() = fileHolders.values.forEach { it.cleanAll() }
  override fun cleanAndAdjustScope(scope: VcsModifiableDirtyScope) = fileHolders.values.forEach { it.cleanAndAdjustScope(scope) }

  override fun copy(): FileHolderComposite = FileHolderComposite(this)

  override fun getType(): FileHolder.HolderType = throw UnsupportedOperationException()

  override fun notifyVcsStarted(vcs: AbstractVcs) = fileHolders.values.forEach { it.notifyVcsStarted(vcs) }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is FileHolderComposite) return false

    return fileHolders == other.fileHolders
  }

  override fun hashCode(): Int = fileHolders.hashCode()
}