// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry

/**
 * The shared helpers of the tests around [KotlinJpsPluginSettings] and the JPS build process parameters.
 */
internal object KotlinJpsPluginSettingsTestUtil {
    /** The message that `checkJpsVersion` reports for [version] when the legacy compiler does not support it. */
    fun legacyCompilerRejectionMessage(version: String): String =
        "The project uses Kotlin '$version', but the legacy Kotlin JPS compiler supports only versions before '2.6.0'. " +
                "Turn on the 'kotlin.jps.use.build.tools.api' registry key to build with the Build Tools API"

    fun setBuildToolsApiEnabled(enabled: Boolean, parentDisposable: Disposable) {
        // The test application registers no default for the Kotlin plugin keys,
        // so `setValue` with a parent disposable fails to read the previous value
        val registryValue = Registry.get("kotlin.jps.use.build.tools.api")
        registryValue.setValue(enabled)
        Disposer.register(parentDisposable) { registryValue.resetToDefault() }
    }

    fun setProjectJpsVersion(project: Project, version: String, parentDisposable: Disposable) {
        KotlinJpsPluginSettings.getInstance(project).setVersion(version)
        // The light fixture can reuse the project across tests, so the version must not survive the test
        Disposer.register(parentDisposable) {
            if (!project.isDisposed) {
                KotlinJpsPluginSettings.getInstance(project).dropExplicitVersion()
            }
        }
    }
}
