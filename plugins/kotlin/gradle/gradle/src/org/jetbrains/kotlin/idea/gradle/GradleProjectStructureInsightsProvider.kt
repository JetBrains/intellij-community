// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.projectStructure.ProjectStructureInsightsProvider
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.plugins.gradle.util.GradleUtil
import org.jetbrains.plugins.gradle.util.isBuildSrcModule
import org.jetbrains.plugins.gradle.util.isIncludedBuild

class GradleProjectStructureInsightsProvider : ProjectStructureInsightsProvider {
    override fun isInSpecialSrcDirectory(psiElement: PsiElement): Boolean {
        if (!RootKindFilter.projectSources.matches(psiElement)) return false
        val module = ModuleUtilCore.findModuleForPsiElement(psiElement) ?: return false
        val moduleData = GradleUtil.findGradleModuleData(module)?.data ?: return false
        return moduleData.isBuildSrcModule() || moduleData.isIncludedBuild
    }
}