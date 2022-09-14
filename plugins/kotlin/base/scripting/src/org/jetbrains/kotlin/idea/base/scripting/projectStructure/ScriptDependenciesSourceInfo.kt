// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.scripting.projectStructure

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.base.scripting.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SourceForBinaryModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.core.script.ucache.getAllScriptDependenciesSourcesScope
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatformAnalyzerServices

sealed class ScriptDependenciesSourceInfo(override val project: Project) : IdeaModuleInfo, SourceForBinaryModuleInfo {
    override val name = Name.special("<Source for script dependencies>")

    override val displayedName: String
        get() = KotlinBaseScriptingBundle.message("source.for.script.dependencies")

    override val binariesModuleInfo: ScriptDependenciesInfo
        get() = ScriptDependenciesInfo.ForProject(project)

    // include project sources because script dependencies sources may contain roots from project
    // the main example is buildSrc for *.gradle.kts files
    override fun sourceScope(): GlobalSearchScope = KotlinSourceFilterScope.projectAndLibrarySources(
        getAllScriptDependenciesSourcesScope(project), project
    )

    override fun hashCode() = project.hashCode()

    override fun equals(other: Any?): Boolean = other is ScriptDependenciesSourceInfo && this.project == other.project

    override val platform: TargetPlatform
        get() = JvmPlatforms.unspecifiedJvmPlatform // TODO(dsavvinov): choose proper TargetVersion

    override val analyzerServices: PlatformDependentAnalyzerServices
        get() = JvmPlatformAnalyzerServices

    class ForProject(project: Project) : ScriptDependenciesSourceInfo(project)
}
