// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class UpdateOptionsDialog(
  project: Project,
  title: @NlsSafe String,
  envToConfMap: LinkedHashMap<Configurable, AbstractVcs>,
) : UpdateOrStatusOptionsDialog(project, title, envToConfMap) {
  override fun getActionNameForDimensions(): @NlsSafe String {
    return "update-v2"
  }

  override fun getDoNotShowMessage(): String {
    return VcsBundle.message("update.checkbox.don.t.show.again")
  }

  override fun isToBeShown(): Boolean {
    return ProjectLevelVcsManagerEx.getInstanceEx(myProject)
      .getOptions(VcsConfiguration.StandardOption.UPDATE).getValue()
  }

  override fun setToBeShown(value: Boolean, onOk: Boolean) {
    if (onOk) {
      ProjectLevelVcsManagerEx.getInstanceEx(myProject)
        .getOptions(VcsConfiguration.StandardOption.UPDATE).setValue(value)
    }
  }
}
