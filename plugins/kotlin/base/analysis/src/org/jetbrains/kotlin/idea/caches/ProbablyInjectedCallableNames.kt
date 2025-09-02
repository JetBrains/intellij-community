// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.caches

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

interface ProbablyInjectedCallableNames {
    fun isProbablyInjectedCallableName(name: String): Boolean

    companion object {
        fun getInstance(project: Project): ProbablyInjectedCallableNames =
            project.service()
    }
}
