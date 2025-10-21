package org.jetbrains.kotlin.base.fir.scripting.projectStructure.modules

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.core.script.v1.ScriptAdditionalIdeaDependenciesProvider
import org.jetbrains.kotlin.idea.core.script.k2.modules.K2IdeScriptAdditionalIdeaDependenciesProvider
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import org.jetbrains.kotlin.utils.exceptions.withVirtualFileEntry

internal class KaScriptModuleImpl(
    override val project: Project,
    override val virtualFile: VirtualFile,
) : KaScriptModuleBase(project, virtualFile) {
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
        K2IdeScriptAdditionalIdeaDependenciesProvider.getRelatedScripts(virtualFile, project)
            .map { KaScriptModuleImpl(project, it) } + ScriptAdditionalIdeaDependenciesProvider.getRelatedModules(virtualFile, project)
            .mapNotNull { it.toKaSourceModuleForProduction() }
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

            val scriptLibraryDependencies = scriptLibraryDependencies(virtualFile)
            scriptLibraryDependencies.forEach(::add)

            val relatedLibraries = K2IdeScriptAdditionalIdeaDependenciesProvider.getRelatedLibraries(virtualFile, project)
            relatedLibraries.forEach {
                project.ideProjectStructureProvider.getKaScriptLibraryModules(it)
            }

            addIfNotNull(sdkDependency)
        }.toList()
    }

    fun scriptLibraryDependencies(virtualFile: VirtualFile): Sequence<KaLibraryModule> {
        val index = currentSnapshot.getVirtualFileUrlIndex()
        val entities = index
            .findEntitiesByUrl(virtualFile.toVirtualFileUrl(virtualFileUrlManager))
            .distinct()
            .filterIsInstance<KotlinScriptEntity>()
            .flatMap { it.dependencies }
            .mapNotNull { currentSnapshot.resolve(it) }

        return entities.flatMap {
            project.ideProjectStructureProvider.getKaScriptLibraryModules(it)
        }
    }
}
