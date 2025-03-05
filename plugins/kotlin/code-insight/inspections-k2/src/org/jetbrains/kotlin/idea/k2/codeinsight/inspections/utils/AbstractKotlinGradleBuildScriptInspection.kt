// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection

abstract class AbstractKotlinGradleBuildScriptInspection : AbstractKotlinInspection() {
    override fun isAvailableForFile(file: PsiFile): Boolean {
        if (file.virtualFile.name == "settings.gradle.kts") return false
        return file.virtualFile.name.endsWith(".gradle.kts")
    }
}
