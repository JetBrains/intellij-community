// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.config

import com.intellij.CvsBundle
import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.cvsSupport2.CvsVcs2
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.configurable.VcsOptionsTopHitProviderBase


private val cvsOptionGroupName get() = CvsBundle.message("general.cvs.display.name")
private fun configuration(project: Project) = CvsConfiguration.getInstance(project)

// @formatter:off
private fun cdUseReadOnlyFlag(project: Project): CheckboxDescriptor =   CheckboxDescriptor(CvsBundle.message("checkbox.use.read.only.flag.for.not.edited.files"), configuration(project)::MAKE_NEW_FILES_READONLY, groupName = cvsOptionGroupName)
private fun cdShowServerOutput(project: Project): CheckboxDescriptor =  CheckboxDescriptor(CvsBundle.message("checkbox.show.cvs.server.output"), configuration(project)::SHOW_OUTPUT, groupName = cvsOptionGroupName)
// @formatter:on

internal class CvsOptionsTopHitProvider : VcsOptionsTopHitProviderBase() {
  override fun getId(): String {
    return "vcs"
  }

  override fun getOptions(project: Project): Collection<OptionDescription> {
    if (isEnabled(project, CvsVcs2.getKey())) {
      return listOf(
        cdUseReadOnlyFlag(project),
        cdShowServerOutput(project)
      ).map(CheckboxDescriptor::asOptionDescriptor)
    }
    return emptyList()
  }
}