// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings

internal fun Project.enableK2Compiler() {
    // The K2 compiler is now bundled to IDEA so we use the bundled version.
}

internal fun Project.enableK1Compiler() {
    KotlinCommonCompilerArgumentsHolder.getInstance(this).update {
        apiVersion = "1.9"
        languageVersion = "1.9"
    }
    KotlinJpsPluginSettings.getInstance(this).setVersion("1.9.25")
}
