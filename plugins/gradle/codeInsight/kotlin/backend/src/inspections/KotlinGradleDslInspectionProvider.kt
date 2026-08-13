// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.codeInsight.kotlin.backend.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.gradle.codeInsight.backend.inspections.GradleDslInspectionProvider
import com.intellij.gradle.codeInsight.kotlin.backend.inspections.visitors.KotlinAvoidApplyPluginMethodInspectionVisitor
import com.intellij.gradle.codeInsight.kotlin.backend.inspections.visitors.KotlinAvoidDependencyNamedArgumentsNotationInspectionVisitor
import com.intellij.gradle.codeInsight.kotlin.backend.inspections.visitors.KotlinAvoidDuplicateDependenciesInspectionVisitor
import com.intellij.gradle.codeInsight.kotlin.backend.inspections.visitors.KotlinAvoidDuplicateRepositoriesInspectionVisitor
import com.intellij.gradle.codeInsight.kotlin.backend.inspections.visitors.KotlinAvoidRepositoriesInBuildGradleInspectionVisitor
import com.intellij.gradle.codeInsight.kotlin.backend.inspections.visitors.KotlinTaskMissingDescriptionInspectionVisitor
import com.intellij.gradle.codeInsight.kotlin.backend.inspections.visitors.RedundantKotlinStdLibInspectionVisitor
import com.intellij.gradle.properties.gradlePropertiesStream
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.gradle.util.GradleConstants

class KotlinGradleDslInspectionProvider : GradleDslInspectionProvider {
    private fun isSuitableGradleKtsFile(file: PsiFile): Boolean =
        file is KtFile && FileUtilRt.extensionEquals(file.name, GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION)

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

    override fun isAvoidDependencyNamedArgumentsNotationInspectionAvailable(file: PsiFile) : Boolean =
        isSuitableGradleKtsFile(file)

    override fun getAvoidDependencyNamedArgumentsNotationInspectionVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        return KotlinAvoidDependencyNamedArgumentsNotationInspectionVisitor(holder)
    }

    override fun isRedundantKotlinStdLibInspectionAvailable(file: PsiFile): Boolean {
        if (!isSuitableGradleKtsFile(file)) return false

        val kotlinStdlibDefaultDependencyProp = gradlePropertiesStream(file).firstNotNullOfOrNull {
            it.findPropertyByKey("kotlin.stdlib.default.dependency")?.value
        }
        // the default value is "true"
        return kotlinStdlibDefaultDependencyProp != "false"
    }

    override fun getRedundantKotlinStdLibInspectionVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        return RedundantKotlinStdLibInspectionVisitor(holder)
    }

    override fun isAvoidApplyPluginMethodInspectionAvailable(file: PsiFile): Boolean =
        isSuitableGradleKtsFile(file)

    override fun getAvoidApplyPluginMethodInspectionVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        return KotlinAvoidApplyPluginMethodInspectionVisitor(holder)
    }

    override fun isAvoidRepositoriesInBuildGradleInspectionAvailable(file: PsiFile): Boolean =
        file is KtFile && FileUtilRt.fileNameEquals(file.name, GradleConstants.KOTLIN_DSL_SCRIPT_NAME)

    override fun getAvoidRepositoriesInBuildGradleInspectionVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        if (!isOnTheFly) return PsiElementVisitor.EMPTY_VISITOR // probably better done interactively
        return KotlinAvoidRepositoriesInBuildGradleInspectionVisitor(holder)
    }

    override fun isAvoidDuplicateDependenciesInspectionAvailable(file: PsiFile): Boolean =
        isSuitableGradleKtsFile(file)

    override fun getAvoidDuplicateDependenciesInspectionVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        return KotlinAvoidDuplicateDependenciesInspectionVisitor(holder, isOnTheFly)
    }

    override fun isTaskMissingDescriptionInspectionAvailable(file: PsiFile): Boolean =
        isSuitableGradleKtsFile(file)

    override fun getTaskMissingDescriptionInspectionVisitor(
        holder: ProblemsHolder,
        onTheFly: Boolean
    ): PsiElementVisitor {
        return KotlinTaskMissingDescriptionInspectionVisitor(holder)
    }

    override fun isAvoidDuplicateRepositoriesInspectionAvailable(file: PsiFile): Boolean =
        isSuitableGradleKtsFile(file)

    override fun getAvoidDuplicateRepositoriesInspectionVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        return KotlinAvoidDuplicateRepositoriesInspectionVisitor(holder)
    }
}