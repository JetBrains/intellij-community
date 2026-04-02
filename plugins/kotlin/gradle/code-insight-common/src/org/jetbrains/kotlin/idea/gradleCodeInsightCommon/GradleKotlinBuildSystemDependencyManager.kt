// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.modcommand.ModCommand
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectNotificationAware
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.psi.PsiFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.util.isGradleModule
import org.jetbrains.kotlin.idea.configuration.GRADLE_SYSTEM_ID
import org.jetbrains.kotlin.idea.configuration.KotlinBuildSystemDependencyManager
import org.jetbrains.kotlin.idea.configuration.KotlinDependencyProvider
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurationService

@ApiStatus.Internal
class GradleKotlinBuildSystemDependencyManager(private val project: Project, private val coroutineScope: CoroutineScope) : KotlinBuildSystemDependencyManager {
    override fun isApplicable(module: Module): Boolean {
        return module.isGradleModule
    }

    @Deprecated(
        "Use addDependencyModCommand instead",
        replaceWith = ReplaceWith("addDependencyModCommand(context, module, libraryDescriptor)")
    )
    override fun addDependency(module: Module, libraryDescriptor: ExternalLibraryDescriptor): Job {
        val scope = libraryDescriptor.preferredScope ?: DependencyScope.COMPILE
        return coroutineScope.launchTracked {
            val manipulator = readAction {
                module.getBuildScriptPsiFile()?.let(GradleBuildScriptSupport::findManipulator)
            } ?: return@launchTracked
            writeAction {
                executeCommand(project = project) {
                    manipulator.addKotlinLibraryToModuleBuildScript(module, scope, libraryDescriptor)
                }
            }
        }
    }

    override fun addDependencyModCommand(contextFile: PsiFile, module: Module, libraryDescriptor: ExternalLibraryDescriptor): ModCommand {
        val scope = libraryDescriptor.preferredScope ?: DependencyScope.COMPILE
        val scriptPsiFile = module.getBuildScriptPsiFile()?: return ModCommand.nop()
        val manipulator = scriptPsiFile.let(GradleBuildScriptSupport::findManipulator) ?: return ModCommand.nop()
        val modCommand =
            manipulator.addKotlinLibraryToModuleBuildScriptModCommand(module, scope, libraryDescriptor)
        return if (modCommand == ModCommand.nop()) {
            modCommand
        } else {
            modCommand
                .andThen(KotlinDependencyProvider.syncModCommand(scriptPsiFile))
        }
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