// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.macros

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Starting from KTIJ-11633 Kotlin JPS plugin is downloaded by IDEA depending on what current version of Kotlin compiler in project
 * settings. Thus, we migrated KOTLIN_BUNDLED from application-wide [PathMacros]/[com.intellij.openapi.application.PathMacroContributor] EP
 * to project-wide EP [com.intellij.openapi.components.impl.ProjectWidePathMacroContributor].
 *
 * Application-wide path macros are visible in the "Path Variables" UI. Since we don't want to confuse users with application-wide
 * KOTLIN_BUNDLED stale value in the UI, we clean the application-wide value in this class
 */
private class ApplicationWideKotlinBundledPathMacroCleaner : ApplicationInitializedListener {
    init {
        if (ApplicationManager.getApplication().isHeadlessEnvironment) {
            throw ExtensionNotApplicableException.create()
        }
    }

    override suspend fun execute(asyncScope: CoroutineScope) {
        asyncScope.launch {
            val pathMacros = ApplicationManager.getApplication().serviceAsync<PathMacros>()
            if (pathMacros.getValue(KOTLIN_BUNDLED) != null) {
                pathMacros.setMacro(KOTLIN_BUNDLED, null)
                // Flush settings otherwise they may stay in config/idea/options/path.macros.xml and then leak into the JPS
                // (because JPS "reimplements" Path macro subsystem and, basically, just reads the `path.macros.xml` file again on its own)
                SaveAndSyncHandler.getInstance().scheduleSave(SaveAndSyncHandler.SaveTask(null, true))
            }
        }
    }
}
