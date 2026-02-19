// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectNotificationAware
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.util.isGradleModule
import org.jetbrains.kotlin.idea.configuration.GRADLE_SYSTEM_ID
import org.jetbrains.kotlin.idea.configuration.KotlinBuildSystemDependencyManager
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurationService

@ApiStatus.Internal
class GradleKotlinBuildSystemDependencyManager(private val project: Project) : KotlinBuildSystemDependencyManager {
    override fun isApplicable(module: Module): Boolean {
        return module.isGradleModule
    }

    override fun addDependency(module: Module, libraryDescriptor: ExternalLibraryDescriptor) {
        val scope = libraryDescriptor.preferredScope ?: DependencyScope.COMPILE
        val buildScriptFile = module.getBuildScriptPsiFile() ?: return
        GradleBuildScriptSupport.findManipulator(buildScriptFile)
            ?.addKotlinLibraryToModuleBuildScript(module, scope, libraryDescriptor)
    }

    override fun isProjectSyncPending(): Boolean {
        val isNotificationVisible =
            ExternalSystemProjectNotificationAware.isNotificationVisibleProperty(project, GRADLE_SYSTEM_ID)
        return isNotificationVisible.get()
    }

    override fun getBuildScriptFile(module: Module): VirtualFile? = module.getBuildScriptPsiFile()?.virtualFile

    override fun isProjectSyncInProgress(): Boolean {
        return KotlinProjectConfigurationService.getInstance(project).isSyncInProgress()
    }

    override fun startProjectSync() {
        KotlinProjectConfigurationService.getInstance(project).queueSync()
    }
}