// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.base.fir.scripting.projectStructure.modules

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaModuleBase
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaScriptModule
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.toKaLibraryModule
import org.jetbrains.kotlin.idea.core.script.v1.KotlinScriptSearchScope
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependencyAware
import org.jetbrains.kotlin.idea.core.script.v1.getLanguageVersionSettings
import org.jetbrains.kotlin.idea.core.script.v1.getPlatform
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import java.util.*

abstract class KaScriptModuleBase(
    override val project: Project,
    open val virtualFile: VirtualFile,
) : KaScriptModule, KaModuleBase() {
    protected val virtualFileUrlManager: VirtualFileUrlManager
        get() = project.workspaceModel.getVirtualFileUrlManager()

    protected val currentSnapshot: ImmutableEntityStorage
        get() = project.workspaceModel.currentSnapshot

    private val scriptDefinition: ScriptDefinition by lazy {
        findScriptDefinition(project, KtFileScriptSource(file))
    }

    override val directDependsOnDependencies: List<KaModule> get() = emptyList()

    override val transitiveDependsOnDependencies: List<KaModule> get() = emptyList()

    override val baseContentScope: GlobalSearchScope
        get() {
            val basicScriptScope = GlobalSearchScope.fileScope(project, virtualFile)
            return KotlinScriptSearchScope(project, basicScriptScope)
        }

    override val targetPlatform: TargetPlatform
        get() = getPlatform(project, virtualFile, scriptDefinition)

    override val languageVersionSettings: LanguageVersionSettings
        get() = getLanguageVersionSettings(project, virtualFile, scriptDefinition)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KaScriptModuleBase

        if (project != other.project) return false
        if (virtualFile != other.virtualFile) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(virtualFile, project)
    }

    override fun toString(): String {
        return "${this::class.simpleName}($virtualFile), platform=$targetPlatform, moduleDescription=`$moduleDescription`, scriptDefinition=`$scriptDefinition`"
    }

    protected val sdkDependencies: List<KaLibraryModule>
        get() = listOfNotNull(ScriptDependencyAware.getInstance(project).getScriptSdk(virtualFile)?.toKaLibraryModule(project))

    override val directFriendDependencies: List<KaModule> get() = emptyList()
}