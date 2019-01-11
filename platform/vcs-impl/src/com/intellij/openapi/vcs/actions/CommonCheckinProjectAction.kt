// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.vcsUtil.VcsUtil
import java.util.stream.Collectors.toList
import java.util.stream.Stream

open class CommonCheckinProjectAction : AbstractCommonCheckinAction() {
  override fun getRoots(context: VcsContext): Array<FilePath> {
    val manager = ProjectLevelVcsManager.getInstance(context.project!!)

    return Stream.of(*manager.allActiveVcss)
      .filter { vcs -> vcs.checkinEnvironment != null }
      .flatMap { vcs -> Stream.of(*manager.getRootsUnderVcs(vcs)) }
      .map { VcsUtil.getFilePath(it) }
      .collect(toList())
      .toTypedArray()
  }

  override fun approximatelyHasRoots(dataContext: VcsContext): Boolean = true

  override fun getActionName(dataContext: VcsContext): String = ActionsBundle.message("action.CheckinProject.text")

  override fun getMnemonicsFreeActionName(context: VcsContext): String = VcsBundle.message("vcs.command.name.checkin.no.mnemonics")
}
