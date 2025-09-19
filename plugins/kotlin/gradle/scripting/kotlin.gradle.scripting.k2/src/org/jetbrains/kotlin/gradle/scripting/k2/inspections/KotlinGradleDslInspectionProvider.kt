// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.gradle.codeInspection.GradleDslInspectionProvider
import org.jetbrains.plugins.gradle.util.GradleConstants

class KotlinGradleDslInspectionProvider : GradleDslInspectionProvider {
    override fun getConfigurationAvoidanceInspectionVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        return PsiElementVisitor.EMPTY_VISITOR
    }

    override fun getForeignDelegateInspectionVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        return PsiElementVisitor.EMPTY_VISITOR
    }

    override fun getIncorrectDependencyNotationArgumentInspectionVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        return PsiElementVisitor.EMPTY_VISITOR
    }

    override fun getDeprecatedConfigurationInspectionVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        return PsiElementVisitor.EMPTY_VISITOR
    }

    override fun getPluginDslStructureInspectionVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        return PsiElementVisitor.EMPTY_VISITOR
    }

    override fun isAvoidDependencyNamedArgumentsNotationInspectionAvailable(file: PsiFile) : Boolean {
        return FileUtilRt.extensionEquals(file.name, GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION)
    }

    override fun getAvoidDependencyNamedArgumentsNotationInspectionVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        return KotlinAvoidDependencyNamedArgumentsNotationInspectionVisitor(holder)
    }
}