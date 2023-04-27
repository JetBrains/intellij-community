// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder

internal fun Project.enableK2Compiler() {
    KotlinCommonCompilerArgumentsHolder.getInstance(this).update {
        useK2 = true
        apiVersion = "2.0"
        languageVersion = "2.0"
    }
}
