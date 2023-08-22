// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.openapi.extensions.ExtensionPointName
import git4idea.repo.GitRepository
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * Supplies a branch presentation to [git4idea.ui.toolbar.GitToolbarWidgetAction]
 */
@ApiStatus.Internal
interface GitCurrentBranchPresenter {
  companion object {
    private val EP_NAME = ExtensionPointName.create<GitCurrentBranchPresenter>("Git4Idea.gitCurrentBranchPresenter")

    fun getPresentation(repository: GitRepository): Presentation? =
      EP_NAME.extensions.firstNotNullOfOrNull { it.getPresentation(repository) }
  }

  fun getPresentation(repository: GitRepository): Presentation?

  data class Presentation(
    val icon: Icon,
    val text: @Nls String,
    val description: @Nls String?
  )
}