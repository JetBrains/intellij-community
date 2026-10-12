// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny

/**
 * Shows the backend dialogs with all unversioned or all ignored files.
 * The scope keeps the RPC call alive while the dialog is open.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class FrontendSpecificFilesDialogService(
  private val project: Project,
  private val cs: CoroutineScope,
) {
  @CalledInAny
  fun showUnversionedFilesDialog() {
    cs.launch {
      ChangesViewApi.getInstance().showUnversionedFilesDialog(project.projectId())
    }
  }

  @CalledInAny
  fun showIgnoredFilesDialog() {
    cs.launch {
      ChangesViewApi.getInstance().showIgnoredFilesDialog(project.projectId())
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): FrontendSpecificFilesDialogService = project.service()
  }
}
