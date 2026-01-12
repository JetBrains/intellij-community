// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations

import com.google.common.collect.TreeMultimap
import com.google.common.graph.Traverser
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.ProgressReporter
import com.intellij.platform.util.progress.reportProgressScope
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.toBuilder
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.core.script.k2.asEntity
import org.jetbrains.kotlin.idea.core.script.k2.highlighting.KotlinScriptResolutionService
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntityProvider
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.modifyKotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.isNonScript
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptReportSink
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import java.io.File
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

@Service(Service.Level.PROJECT)
class MainKtsEntityProvider(
    override val project: Project,
    val coroutineScope: CoroutineScope
) : KotlinScriptEntityProvider(project) {
    private val visitedScripts = TreeMultimap.create(COMPARATOR, COMPARATOR)
    private val visitedScriptsTraverser = Traverser.forTree<VirtualFile> { visitedScripts.get(it) }

    fun getImportedScripts(mainKts: VirtualFile): List<VirtualFile> = visitedScriptsTraverser.breadthFirst(mainKts) - mainKts

    var reporter: ProgressReporter? = null
        private set

    override suspend fun removeKotlinScriptEntity(virtualFile: VirtualFile) {
        visitedScripts.removeAll(virtualFile)
        super.removeKotlinScriptEntity(virtualFile)
    }

    override suspend fun updateWorkspaceModel(
        virtualFile: VirtualFile,
        definition: ScriptDefinition
    ) {
        val scriptUrl = virtualFile.virtualFileUrl
        val mainKtsConfiguration = resolveMainKtsConfiguration(virtualFile, definition)
        val scriptsToResolve = mainKtsConfiguration.importedScripts - visitedScripts.keys()
        if (scriptsToResolve.isNotEmpty()) {
            visitedScripts.putAll(virtualFile, scriptsToResolve)
            KotlinScriptResolutionService.getInstance(project).process(scriptsToResolve)
        }

        fun updateStorage(storage: MutableEntityStorage) {
            val configuration = mainKtsConfiguration.valueOrNull()?.configuration ?: return
            val definition = findScriptDefinition(project, VirtualFileScriptSource(virtualFile))

            val libraryIds = generateScriptLibraryEntities(project, configuration, definition).toList()
            for ((id, sources) in libraryIds) {
                val existingLibrary = storage.resolve(id)
                if (existingLibrary == null) {
                    storage addEntity KotlinScriptLibraryEntity(
                        classes = id.classes,
                        usedInScripts = setOf(scriptUrl),
                        entitySource = MainKtsKotlinScriptEntitySource
                    ) {
                        this.sources += sources
                    }
                } else {
                    storage.modifyKotlinScriptLibraryEntity(existingLibrary) {
                        this.sources += sources
                        this.usedInScripts += scriptUrl
                    }
                }
            }

            storage addEntity KotlinScriptEntity(
                scriptUrl, libraryIds.map { it.first }, MainKtsKotlinScriptEntitySource
            ) {
                this.configuration = configuration.asEntity()
                this.sdkId = configuration.sdkId
            }
        }

        project.updateKotlinScriptEntities(MainKtsKotlinScriptEntitySource) {
            val builder = it.toSnapshot().toBuilder()
            if (builder.getVirtualFileUrlIndex().findEntitiesByUrl(scriptUrl).none()) {
                updateStorage(builder)
                it.applyChangesFrom(builder)
            }
        }

        project.service<ScriptReportSink>().attachReports(virtualFile, mainKtsConfiguration.reports)
    }

    private suspend fun resolveMainKtsConfiguration(
        mainKts: VirtualFile,
        definition: ScriptDefinition
    ): ScriptCompilationConfigurationResult {
        val projectSdk = ProjectRootManager.getInstance(project).projectSdk?.homePath

        val configuration = definition.compilationConfiguration.with {
            projectSdk?.let {
                jvm.jdkHome(File(it))
            }
        }

        val scriptSource = VirtualFileScriptSource(mainKts)

        return withBackgroundProgress(
            project, title = KotlinBaseScriptingBundle.message("progress.title.dependency.resolution", mainKts.name)
        ) {
            reportProgressScope {
                try {
                    reporter = it
                    smartReadAction(project) {
                        refineScriptCompilationConfiguration(scriptSource, definition, project, configuration)
                    }
                } finally {
                    reporter = null
                }
            }
        }
    }

    private val ScriptCompilationConfigurationResult.importedScripts: List<VirtualFile>
        get() {
            val importedScripts = this.valueOrNull()?.importedScripts ?: return emptyList()
            return importedScripts.mapNotNull { (it as? VirtualFileScriptSource)?.virtualFile }.filterNot { it.isNonScript() }
        }

    companion object {
        private val COMPARATOR = Comparator<VirtualFile> { left, right -> left.path.compareTo(right.path) }

        @JvmStatic
        fun getInstance(project: Project): MainKtsEntityProvider = project.service()
    }
}

object MainKtsKotlinScriptEntitySource : EntitySource
