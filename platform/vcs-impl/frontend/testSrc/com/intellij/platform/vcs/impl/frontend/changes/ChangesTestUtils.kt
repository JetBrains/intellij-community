// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.LocalChangeListImpl
import com.intellij.platform.vcs.impl.shared.commit.EditedCommitDetails
import com.intellij.platform.vcs.impl.shared.rpc.ContentRevisionDto
import com.intellij.platform.vcs.impl.shared.rpc.FilePathDto
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.util.VcsUserUtil

internal fun defaultChangeList(project: Project, changePath: FilePath): LocalChangeListImpl = LocalChangeListImpl.Builder(project, "Default")
  .setDefault(true)
  .setChanges(listOf(change(changePath)))
  .build()

internal fun change(path: FilePath, revision: String = "default-revision"): Change {
  val contentRevisionDto = ContentRevisionDto(revision, FilePathDto.toDto(path))
  return Change(null, contentRevisionDto.contentRevision)
}

internal fun createEditedCommit(changes: List<Change>): EditedCommitDetails {
  val message = "Amend commit"
  val author = VcsUserUtil.createUser("John Doe", "john@example.com")
  return EditedCommitDetails(
    currentUser = null,
    committer = author,
    author = author,
    commitHash = HashImpl.build("abc123"),
    subject = message,
    fullMessage = message,
    changes = changes
  )
}