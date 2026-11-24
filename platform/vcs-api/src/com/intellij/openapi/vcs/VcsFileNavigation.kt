// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.requestNavigate
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

@ApiStatus.Internal
object VcsFileNavigation {
  @JvmStatic
  @JvmOverloads
  fun requestNavigateToVcsFile(project: Project, file: VirtualFile, offset: Int = -1): CompletableFuture<Boolean> =
    requestNavigate(project, NavigationOptions.requestFocus()) {
      readAction { listOfNotNull(NavigationRequest.sourceNavigationRequest(project, file, offset)) }
    }

  @JvmStatic
  fun requestNavigateToVcsFiles(project: Project, files: List<VirtualFile>): CompletableFuture<Boolean> {
    var navigation = CompletableFuture.completedFuture(true)
    for (file in files) {
      navigation = navigation.thenCompose { handled ->
        if (handled) requestNavigateToVcsFile(project, file) else CompletableFuture.completedFuture(false)
      }
    }
    return navigation
  }
}
