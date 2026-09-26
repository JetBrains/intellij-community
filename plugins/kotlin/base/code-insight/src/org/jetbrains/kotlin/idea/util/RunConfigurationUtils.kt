// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import com.intellij.execution.RunManager
import com.intellij.psi.PsiElement

internal object RunConfigurationUtils {
    /**
     * Checks if the current run configuration is a Gradle run configuration.
     */
    fun isGradleRunConfiguration(element: PsiElement): Boolean {
        return RunManager.getInstance(element.project).selectedConfiguration?.type?.id == "GradleRunConfiguration"
    }
}