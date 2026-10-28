// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesProvider
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.platform.ide.productMode.IdeProductMode

/**
 * Declares that a project frame of JetBrains Light becomes interactive when its restored editor is shown.
 * The welcome frame of the product keeps its own capabilities.
 */
internal class LightProjectFrameCapabilitiesProvider : ProjectFrameCapabilitiesProvider {
  override fun getCapabilities(project: Project): Set<ProjectFrameCapability> {
    if (!IdeProductMode.isMonolithLight || WelcomeScreenProjectProvider.isWelcomeScreenProject(project)) {
      return emptySet()
    }
    return setOf(ProjectFrameCapability.INTERACTIVE_ON_EDITOR_SHOWN)
  }

  override fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy? = null
}
