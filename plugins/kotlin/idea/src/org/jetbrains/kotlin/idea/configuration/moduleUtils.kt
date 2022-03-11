// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.psi.UserDataProperty

var Module.externalCompilerVersion: String? by UserDataProperty(Key.create("EXTERNAL_COMPILER_VERSION"))

fun Module.findExternalKotlinCompilerVersion(): IdeKotlinVersion? {
    if (getBuildSystemType() == BuildSystemType.JPS) {
        val explicitCompilerArtifactVersion = KotlinJpsPluginSettings.getInstance(project)?.settings?.version
        if (explicitCompilerArtifactVersion != null) {
            return IdeKotlinVersion.opt(explicitCompilerArtifactVersion)
        }

        return KotlinPluginLayout.instance.standaloneCompilerVersion
    }

    val externalCompilerVersion = this.externalCompilerVersion ?: return null
    return IdeKotlinVersion.opt(externalCompilerVersion)
}
