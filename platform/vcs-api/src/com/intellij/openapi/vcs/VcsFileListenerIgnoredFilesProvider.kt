// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface VcsFileListenerIgnoredFilesProvider {
  @RequiresEdt
  fun isDeletionIgnored(project: Project, filePath: FilePath): Boolean

  @RequiresEdt
  fun isAdditionIgnored(project: Project, filePath: FilePath): Boolean

  companion object {
    val EP_NAME: ExtensionPointName<VcsFileListenerIgnoredFilesProvider> =
      ExtensionPointName("com.intellij.vcs.fileListenerIgnoredFilesProvider")

    @JvmStatic
    @RequiresEdt
    fun isAdditionAllowed(project: Project, filePath: FilePath): Boolean {
      val additionIgnoredBy = EP_NAME.findFirstSafe {
        it.isAdditionIgnored(project, filePath)
      }
      if (additionIgnoredBy != null && LOG.isDebugEnabled) {
        LOG.debug("Addition of $filePath is ignored by ${additionIgnoredBy.javaClass}")
      }
      return additionIgnoredBy == null
    }

    @JvmStatic
    @RequiresEdt
    fun isDeletionAllowed(project: Project, filePath: FilePath): Boolean {
      val deletionIgnoredBy = EP_NAME.findFirstSafe {
        it.isDeletionIgnored(project, filePath)
      }
      if (deletionIgnoredBy != null && LOG.isDebugEnabled) {
        LOG.debug("Deletion of $filePath is ignored by ${deletionIgnoredBy.javaClass}")
      }
      return deletionIgnoredBy == null
    }

    private val LOG = logger<VcsFileListenerIgnoredFilesProvider>()
  }
}