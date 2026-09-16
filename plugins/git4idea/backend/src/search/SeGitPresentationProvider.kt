// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.search

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import com.intellij.platform.searchEverywhere.presentations.SeBasicItemPresentationBuilder
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsRef
import icons.DvcsImplIcons
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SeGitPresentationProvider {
  fun getPresentation(item: Any, extendedInfo: SeExtendedInfo?, project: Project, isMultiSelectionSupported: Boolean): SeItemPresentation {
    val icon = if (item is VcsRef)
      DvcsImplIcons.BranchLabel
    else
      AllIcons.Vcs.CommitNode

    val text = when (item) {
      is VcsRef -> item.name
      is VcsCommitMetadata -> item.subject
      else -> ""
    }

    val description = when (item) {
      is VcsRef -> GitSearchUtils.getTrackingRemoteBranchName(item, project)
      is VcsCommitMetadata -> item.id.toShortString()
      else -> null
    }

    return SeBasicItemPresentationBuilder()
      .withIcon(icon)
      .withText(text)
      .withDescription(description)
      .withAccessibleAdditionToText(description)
      .withExtendedInfo(extendedInfo)
      .withMultiSelectionSupported(isMultiSelectionSupported)
      .build()
  }
}