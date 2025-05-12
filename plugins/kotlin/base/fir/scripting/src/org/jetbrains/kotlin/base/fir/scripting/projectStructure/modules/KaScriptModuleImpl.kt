package org.jetbrains.kotlin.base.fir.scripting.projectStructure.modules

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaModuleBase
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaScriptModule
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.toKaLibraryModule
import org.jetbrains.kotlin.idea.base.projectStructure.toKaLibraryModules
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForProduction
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForTest
import org.jetbrains.kotlin.idea.base.scripting.getLanguageVersionSettings
import org.jetbrains.kotlin.idea.base.scripting.getPlatform
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.scriptModuleEntity
import org.jetbrains.kotlin.idea.core.script.ScriptDependencyAware
import org.jetbrains.kotlin.idea.core.script.dependencies.K2IdeScriptAdditionalIdeaDependenciesProvider
import org.jetbrains.kotlin.idea.core.script.dependencies.KotlinScriptSearchScope
import org.jetbrains.kotlin.idea.core.script.dependencies.ScriptAdditionalIdeaDependenciesProvider
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import org.jetbrains.kotlin.utils.exceptions.withVirtualFileEntry
import java.util.*

internal class KaScriptModuleImpl(
    override val project: Project,
    private val scriptFile: VirtualFile,
) : KaScriptModule, KaModuleBase() {

    private val scriptDefinition: ScriptDefinition by lazy {
        findScriptDefinition(project, KtFileScriptSource(file))
    }


    override val file: KtFile
        get() {
            (scriptFile.findPsiFile(project) as? KtFile)?.let { return it }
            val errorMessage = buildString {
                append("KtFile should be alive for ${KaScriptModuleImpl::class.simpleName}")
                if (scriptFile.isValid) {
                    append(", virtualFile: { class: ${scriptFile::class.qualifiedName}, extension: ${scriptFile.extension}, fileType: ${scriptFile.fileType}, fileSystem: ${scriptFile.fileSystem::class.qualifiedName} }")
                } else {
                    append(", virtualFile (${scriptFile::class.qualifiedName}) is invalid")
                }
            }
            errorWithAttachment(errorMessage) {
                withVirtualFileEntry("virtualFile", scriptFile)
            }
        }


    override val directDependsOnDependencies: List<KaModule> get() = emptyList()
    override val transitiveDependsOnDependencies: List<KaModule> get() = emptyList()

    override val directFriendDependencies: List<KaModule> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        K2IdeScriptAdditionalIdeaDependenciesProvider.getRelatedModules(scriptFile, project)
            .mapNotNull { moduleEntity ->
                moduleEntity.entitySource.virtualFileUrl?.virtualFile?.let { KaScriptModuleImpl(project, it) }
            } + ScriptAdditionalIdeaDependenciesProvider.getRelatedModules(scriptFile, project)
            .mapNotNull { it.toKaSourceModuleForProduction() }
    }

    override val baseContentScope: GlobalSearchScope
        get() {
            val basicScriptScope = GlobalSearchScope.fileScope(project, scriptFile)
            return KotlinScriptSearchScope(project, basicScriptScope)
        }

    override val directRegularDependencies: List<KaModule> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildSet {
            val scriptDependencyLibraries = ScriptAdditionalIdeaDependenciesProvider.getRelatedLibraries(scriptFile, project)
            scriptDependencyLibraries.forEach {
                addAll(it.toKaLibraryModules(project))
            }

            val scriptDependentModules = ScriptAdditionalIdeaDependenciesProvider.getRelatedModules(scriptFile, project)
            scriptDependentModules.forEach {
                addIfNotNull(it.toKaSourceModuleForProduction())
                addIfNotNull(it.toKaSourceModuleForTest())
            }

            scriptFile.scriptLibraryDependencies(project).forEach(::add)

            K2IdeScriptAdditionalIdeaDependenciesProvider.getRelatedLibraries(scriptFile, project).forEach {
                addAll(it.library.toKaLibraryModules(project))
            }

            val sdk = ScriptDependencyAware.getInstance(project).getScriptSdk(scriptFile)
            sdk?.let { add(it.toKaLibraryModule(project)) }
        }.toList()
    }

    override val targetPlatform: TargetPlatform
        get() = getPlatform(project, scriptFile, scriptDefinition)

    override val languageVersionSettings: LanguageVersionSettings
        get() = getLanguageVersionSettings(project, scriptFile, scriptDefinition)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is KaScriptModuleImpl
                && scriptFile == other.scriptFile
                && project == other.project
    }

    override fun hashCode(): Int {
        return Objects.hash(scriptFile, project)
    }

    override fun toString(): String {
        return "${this::class.simpleName}($scriptFile), platform=$targetPlatform, moduleDescription=`$moduleDescription`, scriptDefinition=`$scriptDefinition`"
    }
}


fun VirtualFile.scriptLibraryDependencies(project: Project): Sequence<KaLibraryModule> {
    val storage = WorkspaceModel.getInstance(project).currentSnapshot

    val dependencies = scriptModuleEntity(project, storage)?.dependencies ?: emptyList()
    return dependencies.asSequence()
        .mapNotNull {
            (it as? LibraryDependency)?.library?.resolve(storage)
        }.mapNotNull {
            storage.libraryMap.getDataByEntity(it)
        }.flatMap {
            it.toKaLibraryModules(project)
        }
}