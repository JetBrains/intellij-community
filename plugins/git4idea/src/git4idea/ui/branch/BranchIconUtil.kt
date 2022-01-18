// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch

import com.intellij.dvcs.branch.DvcsSyncSettings
import com.intellij.dvcs.repo.Repository
import com.intellij.icons.AllIcons
import com.intellij.ui.LayeredIcon
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import icons.DvcsImplIcons

import javax.swing.Icon

class BranchIconUtil {
  companion object {
    private val INCOMING_LAYERED: Icon = LayeredIcon(AllIcons.Vcs.Branch, DvcsImplIcons.IncomingLayer)
    private val INCOMING_OUTGOING_LAYERED: Icon = LayeredIcon(AllIcons.Vcs.Branch, DvcsImplIcons.IncomingOutgoingLayer)
    private val OUTGOING_LAYERED: Icon = LayeredIcon(AllIcons.Vcs.Branch, DvcsImplIcons.OutgoingLayer)

    @JvmStatic
    fun getBranchIcon(repository: GitRepository): Icon {
      if (repository.state != Repository.State.NORMAL) {
        return AllIcons.General.Warning
      }

      val project = repository.project
      val currentBranchName = repository.currentBranchName
      if (repository.state == Repository.State.NORMAL && currentBranchName != null) {
        val moreThanOneRoot = GitRepositoryManager.getInstance(project).moreThanOneRoot()
        val doNotSyncRepos = GitVcsSettings.getInstance(project).syncSetting == DvcsSyncSettings.Value.DONT_SYNC
        val indicatorRepo = if (moreThanOneRoot && doNotSyncRepos) repository else null

        val incomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(project)
        val hasIncoming = incomingOutgoingManager.hasIncomingFor(indicatorRepo, currentBranchName)
        val hasOutgoing = incomingOutgoingManager.hasOutgoingFor(indicatorRepo, currentBranchName)
        when {
          hasIncoming && hasOutgoing -> return INCOMING_OUTGOING_LAYERED
          hasIncoming -> return INCOMING_LAYERED
          hasOutgoing -> return OUTGOING_LAYERED
        }
      }

      return AllIcons.Vcs.Branch
    }
  }
}