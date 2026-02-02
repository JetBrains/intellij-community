// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.base.fir.scripting.projectStructure.modules

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaModuleBase
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryFallbackDependenciesModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library.KaEntityBasedLibraryModuleScope
import org.jetbrains.kotlin.idea.base.projectStructure.ideProjectStructureProvider
import org.jetbrains.kotlin.idea.base.projectStructure.modules.KaLibraryFallbackDependenciesModuleImpl
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.platform.TargetPlatform
import java.util.Objects

internal class KaScriptDependencyLibraryFallbackDependenciesModule(
    override val dependentLibrary: KaScriptDependencyLibraryModuleImpl
) : KaModuleBase(), KaLibraryFallbackDependenciesModule {
    override val directRegularDependencies: List<KaModule> get() = emptyList()
    override val directDependsOnDependencies: List<KaModule> get() = emptyList()
    override val directFriendDependencies: List<KaModule> get() = emptyList()

    override val baseContentScope: GlobalSearchScope
        get() {
            val scopes = mutableListOf<GlobalSearchScope>()
            val snapshot = project.workspaceModel.currentSnapshot

            val scriptEntity = dependentLibrary.entity.usedInScripts.firstOrNull()?.let {
                snapshot.getVirtualFileUrlIndex().findEntitiesByUrl(it).filterIsInstance<KotlinScriptEntity>().singleOrNull()
            }

            if (scriptEntity != null) {
                scriptEntity.dependencies.forEach { id ->
                    val libraryEntity = snapshot.resolve(id)
                    if (libraryEntity != null) {
                        scopes.add(
                            KaEntityBasedLibraryModuleScope(
                                id, libraryEntity.classes.mapNotNull { it.virtualFile }.toTypedArray(), project
                            )
                        )
                    }
                }

                val sdkLibraryModule = scriptEntity.sdkId?.let { project.ideProjectStructureProvider.getKaLibraryModule(it) }
                if (sdkLibraryModule != null) {
                    scopes.add(sdkLibraryModule.contentScope)
                }
            }

            return if (scopes.isEmpty()) GlobalSearchScope.EMPTY_SCOPE else GlobalSearchScope.union(scopes)
        }

    override val targetPlatform: TargetPlatform
        get() = dependentLibrary.targetPlatform

    override val project: Project
        get() = dependentLibrary.project

    override val moduleDescription: String
        get() = "Fallback dependencies for script dependency module ${dependentLibrary.moduleDescription}"

    override fun equals(other: Any?): Boolean =
        this === other || other is KaLibraryFallbackDependenciesModuleImpl && dependentLibrary == other.dependentLibrary

    override fun hashCode(): Int {
        // Use `Objects.hash` to differentiate this module's hash code from `dependentLibrary`.
        return Objects.hash(dependentLibrary)
    }
}