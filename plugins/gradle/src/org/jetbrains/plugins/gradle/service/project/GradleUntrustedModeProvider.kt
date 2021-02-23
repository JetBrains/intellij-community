// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.ide.impl.UntrustedProjectModeProvider
import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleUntrustedModeProvider: UntrustedProjectModeProvider {
  override fun shouldShowEditorNotification(project: Project): Boolean {
    return !project.isTrusted() && isGradleProject(project)
  }

  private fun isGradleProject(project: Project): Boolean {
    return ExternalSystemApiUtil.getSettings(project, GradleConstants.SYSTEM_ID).linkedProjectsSettings.isNotEmpty()
  }
}