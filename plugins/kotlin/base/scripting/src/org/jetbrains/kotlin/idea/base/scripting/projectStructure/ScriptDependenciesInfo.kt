// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.scripting.projectStructure

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.sequenceOfNotNull
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.base.scripting.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.base.scripting.getLanguageVersionSettings
import org.jetbrains.kotlin.idea.base.scripting.getTargetPlatformVersion
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.dependencies.KotlinScriptSearchScope
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.TargetPlatformVersion
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatformAnalyzerServices
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

sealed class ScriptDependenciesInfo(override val project: Project) : IdeaModuleInfo, BinaryModuleInfo {
    abstract val sdk: Sdk?

    override val name = Name.special("<Script dependencies>")

    override val displayedName: String
        get() = KotlinBaseScriptingBundle.message("script.dependencies")

    override fun dependencies(): List<IdeaModuleInfo> = listOfNotNull(this, sdk?.let { SdkInfo(project, it) })
    override fun dependenciesWithoutSelf(): Sequence<IdeaModuleInfo> = sequenceOfNotNull(sdk?.let { SdkInfo(project, it) })

    // NOTE: intentionally not taking corresponding script info into account
    // otherwise there is no way to implement getModuleInfo
    override fun hashCode() = project.hashCode()

    override fun equals(other: Any?): Boolean = other is ScriptDependenciesInfo && this.project == other.project

    override val moduleOrigin: ModuleOrigin
        get() = ModuleOrigin.LIBRARY

    override val sourcesModuleInfo: SourceForBinaryModuleInfo?
        get() = ScriptDependenciesSourceInfo.ForProject(project)

    override val platform: TargetPlatform
        get() = JvmPlatforms.unspecifiedJvmPlatform // TODO(dsavvinov): choose proper TargetVersion

    override val analyzerServices: PlatformDependentAnalyzerServices
        get() = JvmPlatformAnalyzerServices

    override val moduleContentScope: GlobalSearchScope
        get() = KotlinScriptSearchScope(project, contentScope)

    class ForFile(
        project: Project,
        val scriptFile: VirtualFile,
        val scriptDefinition: ScriptDefinition
    ) : ScriptDependenciesInfo(project), LanguageSettingsOwner {
        override val sdk: Sdk?
            get() = ScriptConfigurationManager.getInstance(project).getScriptSdk(scriptFile)

        override val languageVersionSettings: LanguageVersionSettings
            get() = getLanguageVersionSettings(project, scriptFile, scriptDefinition)

        override val targetPlatformVersion: TargetPlatformVersion
            get() = getTargetPlatformVersion(project, scriptFile, scriptDefinition)

        override val contentScope: GlobalSearchScope
            get() {
                // TODO: this is not very efficient because KotlinSourceFilterScope already checks if the files are in scripts classpath
                val scriptKtFile = PsiManager.getInstance(project).findFile(scriptFile) as KtFile
                val scriptVFile = scriptKtFile.virtualFile ?: scriptKtFile.viewProvider.virtualFile
                return KotlinSourceFilterScope.libraryClasses(
                    ScriptConfigurationManager.getInstance(project).getScriptDependenciesClassFilesScope(scriptVFile), project
 )
            }
    }

    // we do not know which scripts these dependencies are
    class ForProject(project: Project) : ScriptDependenciesInfo(project) {
        override val sdk: Sdk?
            get() {
                return ScriptConfigurationManager.getInstance(project).getFirstScriptsSdk()
            }

        override val contentScope: GlobalSearchScope
            get() {
                return KotlinSourceFilterScope.libraryClasses(
                  ScriptConfigurationManager.getInstance(project).getAllScriptsDependenciesClassFilesScope(), project)
            }

        companion object {
            fun createIfRequired(project: Project, moduleInfos: List<IdeaModuleInfo>): IdeaModuleInfo? =
                if (moduleInfos.any { gradleApiPresentInModule(it) }) ForProject(project) else null

            private fun gradleApiPresentInModule(moduleInfo: IdeaModuleInfo) =
                moduleInfo is JvmLibraryInfo &&
                        !moduleInfo.isDisposed &&
                        moduleInfo.getLibraryRoots().any {
                            // TODO: it's ugly ugly hack as Script (Gradle) SDK has to be provided in case of providing script dependencies.
                            //  So far the indication of usages of  script dependencies by module is `gradleApi`
                            //  Note: to be deleted with https://youtrack.jetbrains.com/issue/KTIJ-19276
                            it.contains("gradle-api")
                        }
        }
    }
}