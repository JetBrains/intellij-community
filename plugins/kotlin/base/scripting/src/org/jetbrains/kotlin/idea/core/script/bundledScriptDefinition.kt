// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.script.IdeConsoleRootType
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.ex.PathUtilEx
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleInfoProvider
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo
import org.jetbrains.kotlin.scripting.definitions.KotlinScriptDefinition
import java.io.File
import kotlin.script.dependencies.Environment
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.dependencies.DependenciesResolver
import kotlin.script.experimental.dependencies.ScriptDependencies
import kotlin.script.experimental.dependencies.asSuccess
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContextOrStdlib
import kotlin.script.templates.standard.ScriptTemplateWithArgs


class BundledScriptDefinitionContributor(val project: Project) : ScriptDefinitionContributor {
    private val myBundledIdeScriptDefinition = BundledIdeScriptDefinition(project)

    @Deprecated("migrating to new configuration refinement: use ScriptDefinitionsSource instead")
    override fun getDefinitions() = listOf(myBundledIdeScriptDefinition)

    @Deprecated("migrating to new configuration refinement: drop usages")
    override val id: String = "StandardKotlinScript"
}


class BundledIdeScriptDefinition internal constructor(project: Project) : KotlinScriptDefinition(ScriptTemplateWithArgs::class) {
    override val dependencyResolver = BundledKotlinScriptDependenciesResolver(project)
}


class BundledKotlinScriptDependenciesResolver(private val project: Project) : DependenciesResolver {
    override fun resolve(
        scriptContents: ScriptContents,
        environment: Environment
    ): DependenciesResolver.ResolveResult {
        val virtualFile = scriptContents.file?.let { VfsUtil.findFileByIoFile(it, true) }

        val javaHome = getScriptSDK(project, virtualFile)

        val classpath = buildList {
            if (ScratchFileService.getInstance().getRootType(virtualFile) is IdeConsoleRootType) {
                addAll(scriptCompilationClasspathFromContextOrStdlib(wholeClasspath = true))
            }
            add(KotlinArtifacts.kotlinReflect)
            add(KotlinArtifacts.kotlinStdlib)
            add(KotlinArtifacts.kotlinScriptRuntime)
        }

        return ScriptDependencies(javaHome = javaHome?.let { File(it)}, classpath = classpath).asSuccess()
    }

    private fun getScriptSDK(project: Project, virtualFile: VirtualFile?): String? {
        if (virtualFile != null) {
            for (result in ModuleInfoProvider.getInstance(project).collect(virtualFile)) {
                val moduleInfo = result.getOrNull() ?: break
                val sdk = moduleInfo.dependencies().asSequence().filterIsInstance<SdkInfo>().singleOrNull()?.sdk ?: continue
                return sdk.homePath
            }
        }

        val jdk = ProjectRootManager.getInstance(project).projectSdk
            ?: runReadAction { ProjectJdkTable.getInstance() }.allJdks
                .firstOrNull { sdk -> sdk.sdkType is JavaSdk }
            ?: PathUtilEx.getAnyJdk(project)
        return jdk?.homePath
    }
}