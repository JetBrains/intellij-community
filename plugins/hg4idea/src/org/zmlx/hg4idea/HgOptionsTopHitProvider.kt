// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.configurable.VcsOptionsTopHitProviderBase
import com.intellij.ui.layout.*

private val hgOptionGroupName get() = HgBundle.message("hg4idea.mercurial")
private fun configuration(project: Project) = HgProjectSettings.getInstance(project)

// @formatter:off
private fun cdCheckIncomingOutgoing(project: Project): CheckboxDescriptor =           CheckboxDescriptor(HgBundle.message("hg4idea.configuration.check.incoming.outgoing"), PropertyBinding({ configuration(project).isCheckIncomingOutgoing }, { configuration(project).isCheckIncomingOutgoing = it }), groupName = hgOptionGroupName)
private fun cdIgnoreWhitespacesInAnnotations(project: Project): CheckboxDescriptor =  CheckboxDescriptor(HgBundle.message("hg4idea.configuration.ignore.whitespace.in.annotate"), PropertyBinding({ configuration(project).isWhitespacesIgnoredInAnnotations }, { configuration(project).setIgnoreWhitespacesInAnnotations(it) }), groupName = hgOptionGroupName)
// @formatter:on

internal class HgOptionsTopHitProvider : VcsOptionsTopHitProviderBase() {
  override fun getId(): String {
    return "vcs"
  }

  override fun getOptions(project: Project): Collection<OptionDescription> {
    if (isEnabled(project, HgVcs.getKey())) {
      return listOf(
        cdCheckIncomingOutgoing(project),
        cdIgnoreWhitespacesInAnnotations(project)
      ).map(CheckboxDescriptor::asOptionDescriptor)
    }
    return emptyList()
  }
}