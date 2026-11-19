// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

import com.intellij.openapi.project.Project

/**
 * Dumps the settings context over the roots of [SettingsRootsCache]. The monolith and a remote-development backend
 * both use it. Its frontend twin is `DumpFrontendSettingsContextAction` in `intellij.platform.settings.v2.frontend.split`.
 */
internal class DumpSettingsContextAction : SettingsContextDumpAction() {
  override fun rootUrls(project: Project): List<String> =
    SettingsRootsCache.getInstance(project).roots().map { it.url }
}
