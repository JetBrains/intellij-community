// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import org.jetbrains.kotlin.analysis.project.structure.KtBuiltinsModule
import org.jetbrains.kotlin.analysis.project.structure.KtLibraryModule
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.KtScriptDependencyModule
import org.jetbrains.kotlin.analysis.project.structure.KtScriptModule
import org.jetbrains.kotlin.analysis.project.structure.KtSdkModule
import org.jetbrains.kotlin.analysis.project.structure.impl.KotlinModuleDependentsProviderBase
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

/**
 * [IdeKotlinModuleDependentsProvider] provides [KtModule] dependents by querying the workspace model.
 */
internal class IdeKotlinModuleDependentsProvider(private val project: Project) : KotlinModuleDependentsProviderBase() {
    override fun getDirectDependents(module: KtModule): Set<KtModule> {
        val symbolicId = when (module) {
            is KtSourceModuleByModuleInfo -> module.moduleId
            is KtLibraryModuleByModuleInfo -> module.libraryId ?: return emptySet()
            is KtLibrarySourceModuleByModuleInfo -> return getDirectDependents(module.binaryLibrary)

            // No dependents need to be provided for `KtSdkModule` and `KtBuiltinsModule` (see `KotlinModuleDependentsProvider`).
            is KtSdkModule -> return emptySet()
            is KtBuiltinsModule -> return emptySet()

            // Script modules are not supported yet (see KTIJ-25620).
            is KtScriptModule, is KtScriptDependencyModule -> return emptySet()

            is NotUnderContentRootModuleByModuleInfo -> return emptySet()

            else -> throw KotlinExceptionWithAttachments("Unexpected ${KtModule::class.simpleName}").withAttachment("module.txt", module)
        }

        val snapshot = WorkspaceModel.getInstance(project).currentSnapshot
        return snapshot
            .referrers(symbolicId, ModuleEntity::class.java)
            .flatMapTo(mutableSetOf()) { moduleEntity ->
                // The set of dependents should not include `module` itself.
                if (moduleEntity.symbolicId == symbolicId) return@flatMapTo emptyList()

                // We can skip the module entity if `findModule` returns `null` because the module won't have been added to the project
                // model yet and thus cannot be a proper `KtModule`.
                moduleEntity.findModule(snapshot)?.sourceModuleInfos?.map { it.toKtModule() } ?: emptyList()
            }
    }
}
