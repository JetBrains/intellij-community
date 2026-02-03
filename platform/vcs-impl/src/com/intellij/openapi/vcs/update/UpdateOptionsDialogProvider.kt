// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.AbstractVcs

interface UpdateOptionsDialogProvider {
  fun create(
    project: Project,
    title: @NlsSafe String,
    envToConfMap: LinkedHashMap<Configurable, AbstractVcs>,
  ): UpdateOrStatusOptionsDialog?

  companion object {
    val EP_NAME: ExtensionPointName<UpdateOptionsDialogProvider> = ExtensionPointName.create("com.intellij.vcs.updateOptionsDialogProvider")

    @JvmStatic
    fun createOptionsDialog(project: Project, title: @NlsSafe String, envToConfMap: LinkedHashMap<Configurable, AbstractVcs>): UpdateOrStatusOptionsDialog {
      val dialog = EP_NAME.computeSafeIfAny { provider ->
        provider.create(project, title, envToConfMap)
      }
      return dialog ?: UpdateOptionsDialog(project, title, envToConfMap)
    }
  }
}