package org.jetbrains.kotlin.base.fir.scripting.projectStructure.modules

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.base.projectStructure.ideProjectStructureProvider
import org.jetbrains.kotlin.idea.base.projectStructure.toKaLibraryModule
import org.jetbrains.kotlin.idea.base.projectStructure.toKaLibraryModules
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForProduction
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForTest
import org.jetbrains.kotlin.idea.core.script.k2.asCompilationConfiguration
import org.jetbrains.kotlin.idea.core.script.k2.getVirtualFile
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.v1.ScriptAdditionalIdeaDependenciesProvider
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.resolvedImportScripts
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import org.jetbrains.kotlin.utils.exceptions.withVirtualFileEntry
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.importScripts

internal class KaScriptModuleImpl(
    override val project: Project,
    override val virtualFile: VirtualFile,
    override val snapshot: ImmutableEntityStorage,
) : KaScriptModuleBase(project, virtualFile) {
    val kotlinScriptEntity by lazy(LazyThreadSafetyMode.PUBLICATION) {
        snapshot.scriptEntitiesByUrl(virtualFile.toVirtualFileUrl(virtualFileUrlManager)).singleOrNull()
    }

    private val importedScriptUrls: List<VirtualFileUrl> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        kotlinScriptEntity?.let { getImportedScripts(it) }.orEmpty()
    }

    override val file: KtFile
        get() {
            (virtualFile.findPsiFile(project) as? KtFile)?.let { return it }
            val errorMessage = buildString {
                append("KtFile should be alive for ${KaScriptModuleImpl::class.simpleName}")
                if (virtualFile.isValid) {
                    append(", virtualFile: { class: ${virtualFile::class.qualifiedName}, extension: ${virtualFile.extension}, fileType: ${virtualFile.fileType}, fileSystem: ${virtualFile.fileSystem::class.qualifiedName} }")
                } else {
                    append(", virtualFile (${virtualFile::class.qualifiedName}) is invalid")
                }
            }
            errorWithAttachment(errorMessage) {
                withVirtualFileEntry("virtualFile", virtualFile)
            }
        }

    override val directFriendDependencies: List<KaModule> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildList {
            kotlinScriptEntity?.relatedModuleIds?.forEach {
                addIfNotNull(it.toKaSourceModuleForProduction(project))
            }

            importedScriptUrls.mapNotNull { it.virtualFile }.forEach {
                add(KaScriptModuleImpl(project, it, snapshot))
            }

            addAll(
                ScriptAdditionalIdeaDependenciesProvider.getRelatedModules(virtualFile, project).mapNotNull {
                    it.toKaSourceModuleForProduction()
                })
        }
    }

    private fun importedScriptLibraries() =
        importedScriptUrls.asSequence()
            .flatMap { snapshot.scriptEntitiesByUrl(it) }
            .flatMap { it.dependencies }
            .mapNotNull { snapshot.resolve(it) }
            .distinct()

    private fun ImmutableEntityStorage.scriptEntitiesByUrl(url: VirtualFileUrl): Sequence<KotlinScriptEntity> =
        getVirtualFileUrlIndex().findEntitiesByUrl(url).filterIsInstance<KotlinScriptEntity>()

    private fun getImportedScripts(
        entity: KotlinScriptEntity,
        visited: MutableSet<VirtualFileUrl> = mutableSetOf(),
    ): List<VirtualFileUrl> {
        val importedScriptUrls = entity.configurationId?.let {
            snapshot.resolve(it)
        }?.data?.asCompilationConfiguration()?.let {
            it[ScriptCompilationConfiguration.resolvedImportScripts] ?: it[ScriptCompilationConfiguration.importScripts]
        }.orEmpty().mapNotNull {
            getVirtualFile(it)?.toVirtualFileUrl(virtualFileUrlManager)
        }.filter { visited.add(it) }

        return importedScriptUrls + importedScriptUrls.flatMap {
            snapshot.scriptEntitiesByUrl(it).flatMap { entity -> getImportedScripts(entity, visited) }
        }
    }

    override val directRegularDependencies: List<KaModule> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildSet {
            val scriptDependencyLibraries = ScriptAdditionalIdeaDependenciesProvider.getRelatedLibraries(virtualFile, project)
            scriptDependencyLibraries.forEach {
                addAll(it.toKaLibraryModules(project))
            }

            val scriptDependentModules = ScriptAdditionalIdeaDependenciesProvider.getRelatedModules(virtualFile, project)
            scriptDependentModules.forEach {
                addIfNotNull(it.toKaSourceModuleForProduction())
                addIfNotNull(it.toKaSourceModuleForTest())
            }

            addRegularDependencies()

            importedScriptLibraries().forEach {
                addAll(project.ideProjectStructureProvider.getKaScriptLibraryModules(it))
            }
        }.toList()
    }

    private fun MutableCollection<KaModule>.addRegularDependencies() {
        val libraryDependencies = kotlinScriptEntity?.dependencies?.mapNotNull { snapshot.resolve(it) }?.flatMap {
            project.ideProjectStructureProvider.getKaScriptLibraryModules(it)
        } ?: emptyList()

        addAll(libraryDependencies)

        addIfNotNull(kotlinScriptEntity?.sdkId?.toKaLibraryModule(project))
    }
}
