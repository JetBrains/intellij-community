// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.idea.KotlinVersionVerbose
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.psi.UserDataProperty

var Module.externalCompilerVersion: String? by UserDataProperty(Key.create("EXTERNAL_COMPILER_VERSION"))

fun Module.findExternalKotlinCompilerVersion(): KotlinVersionVerbose? {
    val externalCompilerVersion = (if (getBuildSystemType() == BuildSystemType.JPS) {
        KotlinJpsPluginSettings.getInstance(project)?.settings?.version ?: KotlinPluginLayout.instance.standaloneCompilerVersion
    } else {
        this.externalCompilerVersion
    }) ?: return null

    return KotlinVersionVerbose.parse(externalCompilerVersion)
}
