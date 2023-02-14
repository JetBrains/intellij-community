// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.resolve.lazy

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

interface ProbablyContractedCallableNames {
    fun isProbablyContractedCallableName(name: String): Boolean

    companion object {
        fun getInstance(project: Project): ProbablyContractedCallableNames =
            project.service()
    }
}