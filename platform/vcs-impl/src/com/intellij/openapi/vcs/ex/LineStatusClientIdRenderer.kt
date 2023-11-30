// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

@ApiStatus.Internal
interface LineStatusClientIdRenderer {
  fun isEnabled(): Boolean
  fun getIcon(clientIds: List<ClientId>): Icon
  fun getTooltipText(clientIds: List<ClientId>): @Nls String?
  fun getClickAction(clientIds: List<ClientId>): AnAction?

  companion object {
    val EP_NAME: ProjectExtensionPointName<LineStatusClientIdRenderer> =
      ProjectExtensionPointName("com.intellij.vcs.lineStatusClientIdRenderer")

    fun getInstance(project: Project): LineStatusClientIdRenderer? {
      return EP_NAME.getExtensions(project).find { it.isEnabled() }
    }
  }
}
