// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.vcs.log.VcsCommitMetadata

interface DiffRevisionMetadataProvider {
  companion object {
    val EP_NAME: ExtensionPointName<DiffRevisionMetadataProvider> =
      ExtensionPointName.create<DiffRevisionMetadataProvider>("com.intellij.vcs.diffRevisionMetadataProvider")
  }

  fun canApply(contentRevision: ContentRevision): Boolean

  suspend fun getMetadata(project: Project, contentRevision: ContentRevision): VcsCommitMetadata?
}