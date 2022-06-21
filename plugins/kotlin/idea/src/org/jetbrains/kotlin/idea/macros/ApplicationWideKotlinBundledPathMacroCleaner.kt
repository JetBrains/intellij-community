// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.macros

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.application.PathMacros

/**
 * Starting from KTIJ-11633 Kotlin JPS plugin is downloaded by IDEA depending on what current version of Kotlin compiler in project
 * settings. Thus, we migrated KOTLIN_BUNDLED from application-wide [PathMacros]/[com.intellij.openapi.application.PathMacroContributor] EP
 * to project-wide EP [com.intellij.openapi.components.impl.ProjectWidePathMacroContributor].
 *
 * Application-wide path macros are visible in the "Path Variables" UI. Since we don't want to confuse users with application-wide
 * KOTLIN_BUNDLED stale value in the UI, we clean the application-wide value in this class
 */
class ApplicationWideKotlinBundledPathMacroCleaner {
    init {
        if (PathMacros.getInstance().getValue(KOTLIN_BUNDLED) != null) {
            PathMacros.getInstance().setMacro(KOTLIN_BUNDLED, null)
            // Flush settings otherwise they may stay in config/idea/options/path.macros.xml and then leak into the JPS
            // (because JPS "reimplements" Path macro subsystem and, basically, just reads the `path.macros.xml` file again on its own)
            SaveAndSyncHandler.getInstance().scheduleSave(SaveAndSyncHandler.SaveTask(null, true))
        }
    }
}
