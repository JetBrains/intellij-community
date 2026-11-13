// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core

import com.intellij.ide.actions.CreateJavaClassActionAvailabilityOverride
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiDirectory
import com.intellij.util.ThreeState
import org.jetbrains.kotlin.idea.base.util.KOTLIN_SOURCE_ROOT_TYPES
import org.jetbrains.kotlin.idea.facet.KotlinFacet

internal class KotlinJavaClassActionSuppressor : CreateJavaClassActionAvailabilityOverride {
    override fun isActionAvailable(directories: Array<out PsiDirectory>): ThreeState {
        if (AdvancedSettings.getBoolean("java.suggest.creating.java.sources.in.irrelevant.source.roots")) return ThreeState.UNSURE

        for (directory in directories) {
            val virtualFile = directory.virtualFile
            val projectFileIndex = ProjectRootManager.getInstance(directory.project).fileIndex
            val module = projectFileIndex.getModuleForFile(virtualFile) ?: continue
            if (projectFileIndex.isUnderSourceRootOfType(virtualFile, KOTLIN_SOURCE_ROOT_TYPES)) return ThreeState.NO
            val sourceRootPath = projectFileIndex.getSourceRootForFile(virtualFile)?.toNioPath()?.toString() ?: continue

            val kotlinFacet = KotlinFacet.get(module)
            val kotlinSourceFolders = kotlinFacet?.configuration?.settings?.pureKotlinSourceFolders ?: continue
            val kotlinSourceRootOfType = kotlinSourceFolders.any { sourceRootPath.startsWith(it, ignoreCase = true) }
            if (kotlinSourceRootOfType) return ThreeState.NO
        }
        return ThreeState.UNSURE
    }
}
