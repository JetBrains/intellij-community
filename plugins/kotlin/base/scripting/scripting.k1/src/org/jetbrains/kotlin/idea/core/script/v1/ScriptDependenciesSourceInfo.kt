// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.v1

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SourceForBinaryModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatformAnalyzerServices

@K1ModeProjectStructureApi
sealed class ScriptDependenciesSourceInfo(override val project: Project) : IdeaModuleInfo, SourceForBinaryModuleInfo {
    override val name = Name.special("<Source for script dependencies>")

    override val displayedName: String
        get() = KotlinBaseScriptingBundle.message("source.for.script.dependencies")

    override val binariesModuleInfo: ScriptDependenciesInfo
        get() = ScriptDependenciesInfo.ForProject(project)

    // include project sources because script dependencies sources may contain roots from project
    // the main example is buildSrc for *.gradle.kts files
    override fun sourceScope(): GlobalSearchScope =
        KotlinSourceFilterScope.Companion.projectAndLibrarySources(
          ScriptDependencyAware.Companion.getInstance(project).getAllScriptDependenciesSourcesScope(), project)

    override fun hashCode() = project.hashCode()

    override fun equals(other: Any?): Boolean = other is ScriptDependenciesSourceInfo && this.project == other.project

    override val platform: TargetPlatform
        get() = JvmPlatforms.unspecifiedJvmPlatform // TODO(dsavvinov): choose proper TargetVersion

    override val analyzerServices: PlatformDependentAnalyzerServices
        get() = JvmPlatformAnalyzerServices

    class ForProject(project: Project) : ScriptDependenciesSourceInfo(project)
}