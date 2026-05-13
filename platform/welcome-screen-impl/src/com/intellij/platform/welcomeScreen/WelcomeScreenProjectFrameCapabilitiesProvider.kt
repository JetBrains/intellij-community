// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.welcomeScreen

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesProvider
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider

internal class WelcomeScreenProjectFrameCapabilitiesProvider : ProjectFrameCapabilitiesProvider {
  /**
   * Maps welcome-screen project classification to generic frame capabilities.
   *
   * Startup UI policy is intentionally not provided here; it is contributed by module-specific
   * providers that consume [ProjectFrameCapability.WELCOME_EXPERIENCE].
   */
  override fun getCapabilities(project: Project): Set<ProjectFrameCapability> {
    if (!WelcomeScreenProjectProvider.isWelcomeScreenProject(project)) {
      return emptySet()
    }

    return buildSet {
      add(ProjectFrameCapability.WELCOME_EXPERIENCE)

      if (!WelcomeScreenProjectProvider.isVcsEnabled(project)) {
        add(ProjectFrameCapability.SUPPRESS_VCS_UI)
      }

      if (WelcomeScreenProjectProvider.isForceDisabledFileColors()) {
        add(ProjectFrameCapability.FORCE_DISABLE_FILE_COLORS)
      }
    }
  }

  override fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy? {
    return null
  }
}
