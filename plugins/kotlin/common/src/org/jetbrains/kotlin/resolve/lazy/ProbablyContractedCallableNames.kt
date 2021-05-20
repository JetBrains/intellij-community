/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.lazy

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.util.application.getServiceSafe

interface ProbablyContractedCallableNames {
    fun isProbablyContractedCallableName(name: String): Boolean

    companion object {
        fun getInstance(project: Project): ProbablyContractedCallableNames =
            project.getServiceSafe()
    }
}