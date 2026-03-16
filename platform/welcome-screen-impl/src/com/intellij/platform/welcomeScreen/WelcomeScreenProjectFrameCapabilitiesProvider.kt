// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.welcomeScreen

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesProvider
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import java.util.EnumSet

internal class WelcomeScreenProjectFrameCapabilitiesProvider : ProjectFrameCapabilitiesProvider {
  /**
   * Maps welcome-screen project classification to generic frame capabilities.
   *
   * Startup UI policy is intentionally not provided here; it is contributed by module-specific
   * providers that consume [ProjectFrameCapability.WELCOME_EXPERIENCE].
   */
  override fun getCapabilities(project: Project): Set<ProjectFrameCapability> {
    if (!WelcomeScreenProjectProvider.Companion.isWelcomeScreenProject(project)) {
      return emptySet()
    }

    if (WelcomeScreenProjectProvider.Companion.isForceDisabledFileColors()) {
      return WELCOME_CAPABILITIES_WITH_DISABLED_FILE_COLORS
    }
    else {
      return WELCOME_CAPABILITIES
    }
  }

  override fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy? {
    return null
  }
}

private val WELCOME_CAPABILITIES: EnumSet<ProjectFrameCapability> =
  EnumSet.of(
    ProjectFrameCapability.WELCOME_EXPERIENCE,
    ProjectFrameCapability.SUPPRESS_VCS_UI,
  )

private val WELCOME_CAPABILITIES_WITH_DISABLED_FILE_COLORS: EnumSet<ProjectFrameCapability> =
  EnumSet.copyOf(WELCOME_CAPABILITIES).apply {
    add(ProjectFrameCapability.FORCE_DISABLE_FILE_COLORS)
  }
