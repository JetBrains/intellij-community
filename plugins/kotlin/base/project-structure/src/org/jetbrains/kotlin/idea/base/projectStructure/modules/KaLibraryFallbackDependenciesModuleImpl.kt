// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.modules

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaModuleBase
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryFallbackDependenciesModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.platform.TargetPlatform
import java.util.Objects

/**
 * A common implementation for [KaLibraryFallbackDependenciesModule], which is not backed by any specific module info/workspace model
 * entity.
 */
class KaLibraryFallbackDependenciesModuleImpl(override val dependentLibrary: KaLibraryModule) : KaModuleBase(), KaLibraryFallbackDependenciesModule {
    override val directRegularDependencies: List<KaModule> get() = emptyList()
    override val directDependsOnDependencies: List<KaModule> get() = emptyList()
    override val directFriendDependencies: List<KaModule> get() = emptyList()

    override val baseContentScope: GlobalSearchScope
        get() = ProjectScope.getLibrariesScope(project).intersectWith(GlobalSearchScope.notScope(dependentLibrary.contentScope))

    override val targetPlatform: TargetPlatform
        get() = dependentLibrary.targetPlatform

    override val project: Project
        get() = dependentLibrary.project

    @KaExperimentalApi
    override val moduleDescription: String
        get() = "Fallback dependencies for library module `${dependentLibrary.moduleDescription}`"

    override fun equals(other: Any?): Boolean =
        this === other || other is KaLibraryFallbackDependenciesModuleImpl && dependentLibrary == other.dependentLibrary

    override fun hashCode(): Int {
        // Use `Objects.hash` to differentiate this module's hash code from `dependentLibrary`.
        return Objects.hash(dependentLibrary)
    }
}
