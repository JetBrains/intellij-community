// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.openapi.components.service
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.ui.dsl.builder.Panel
import git4idea.config.GitVcsApplicationSettings
import git4idea.i18n.GitBundle

class GitLogCommitEditingConfigurable : UiDslUnnamedConfigurable.Simple() {
  override fun Panel.createContent() {
    val settings = service<GitVcsApplicationSettings>()

    row {
      checkBox(CheckboxDescriptor(
        GitBundle.message("settings.show.rebase.log.drop.confirmation"),
        { settings.isShowDropCommitDialog },
        { settings.isShowDropCommitDialog = it },
        comment = GitBundle.message("settings.show.rebase.log.drop.confirmation.description")
      ))
    }
  }
}