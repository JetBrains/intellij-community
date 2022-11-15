// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.dependencies

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.roots.impl.CustomEntityProjectModelInfoProvider
import com.intellij.openapi.roots.impl.CustomEntityProjectModelInfoProvider.LibraryRoots
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspaceModel.ide.impl.virtualFile
import com.intellij.workspaceModel.storage.EntityStorage
import org.jetbrains.deft.annotations.Child
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.scripting.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryRootTypeId
import org.jetbrains.kotlin.idea.core.script.ucache.scriptsAsEntities
import javax.swing.Icon

/**
 * See recommendations for custom entities indexing
 * [here](https://youtrack.jetbrains.com/articles/IDEA-A-239/Integration-of-custom-workspace-entities-with-platform-functionality)
 */
class KotlinScriptProjectModelInfoProvider : CustomEntityProjectModelInfoProvider<KotlinScriptEntity> {
    override fun getEntityClass(): Class<KotlinScriptEntity> = KotlinScriptEntity::class.java

    override fun getLibraryRoots(
        entities: Sequence<KotlinScriptEntity>,
        entityStorage: EntityStorage
    ): Sequence<LibraryRoots<KotlinScriptEntity>> =
        if (!scriptsAsEntities) { // see KotlinScriptDependenciesLibraryRootProvider
            emptySequence()
        } else {
            entities.flatMap { scriptEntity ->
                scriptEntity.dependencies.map<@Child KotlinScriptLibraryEntity, LibraryRoots<KotlinScriptEntity>> { libEntity ->
                    val (classes, sources) = libEntity.roots.partition { it.type == KotlinScriptLibraryRootTypeId.COMPILED }
                    val classFiles = classes.mapNotNull { it.url.virtualFile }
                    val sourceFiles = sources.mapNotNull { it.url.virtualFile }
                    LibraryRoots(scriptEntity, sourceFiles, classFiles, emptyList(), null)
                }
            }
        }
}


class KotlinScriptDependenciesLibraryRootProvider : AdditionalLibraryRootsProvider() {

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> { // RootIndex & FileBasedIndexEx need it
        if (scriptsAsEntities) return emptyList() // see KotlinScriptProjectModelInfoProvider

        val manager = ScriptConfigurationManager.getInstance(project)
        val classes = manager.getAllScriptsDependenciesClassFiles().filterValid()
        val sources = manager.getAllScriptDependenciesSources().filterValid()
        val sdkClasses = manager.getAllScriptsSdkDependenciesClassFiles().filterValid()
        val sdkSources = manager.getAllScriptSdkDependenciesSources().filterValid()
        return if (classes.isEmpty() && sources.isEmpty() && sdkClasses.isEmpty() && sdkSources.isEmpty()) {
            emptyList()
        } else {
            val library = KotlinScriptDependenciesLibrary(classes = classes, sources = sources)
            if (sdkClasses.isEmpty() && sdkSources.isEmpty()) {
                listOf(library)
            } else {
                listOf(ScriptSdk(manager.getFirstScriptsSdk(), sdkClasses, sdkSources), library)
            }
        }
    }

    private fun Collection<VirtualFile>.filterValid() = this.filterTo(LinkedHashSet(), VirtualFile::isValid)

    override fun getRootsToWatch(project: Project): Collection<VirtualFile> = if (scriptsAsEntities) {
        emptyList()
    } else {
        ScriptConfigurationManager.allExtraRoots(project).filterValid()
    }

    abstract class AbstractDependenciesLibrary(private val id: String, val classes: Collection<VirtualFile>, val sources: Collection<VirtualFile>) :
        SyntheticLibrary(id, null), ItemPresentation {

        protected val gradle: Boolean by lazy { classes.hasGradleDependency() }

        override fun getBinaryRoots(): Collection<VirtualFile> = classes

        override fun getSourceRoots(): Collection<VirtualFile> = sources

        override fun getIcon(unused: Boolean): Icon = if (gradle) {
            KotlinIcons.GRADLE_SCRIPT
        } else {
            KotlinIcons.SCRIPT
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as AbstractDependenciesLibrary

            return id == other.id && classes == other.classes && sources == other.sources
        }

        override fun hashCode(): Int {
            return 31 * classes.hashCode() + sources.hashCode()
        }


    }

    private class KotlinScriptDependenciesLibrary(classes: Collection<VirtualFile>, sources: Collection<VirtualFile>) :
        AbstractDependenciesLibrary("KotlinScriptDependenciesLibrary", classes, sources) {

        override fun getPresentableText(): String =
            if (gradle) {
                KotlinBaseScriptingBundle.message("script.name.gradle.script.dependencies")
            } else {
                KotlinBaseScriptingBundle.message("script.name.kotlin.script.dependencies")
            }
    }

    private class ScriptSdk(val sdk: Sdk?, classes: Collection<VirtualFile>, sources: Collection<VirtualFile>) :
        AbstractDependenciesLibrary("ScriptSdk", classes, sources) {

        override fun getPresentableText(): String =
            if (gradle) {
                sdk?.let { KotlinBaseScriptingBundle.message("script.name.gradle.script.sdk.dependencies.0", it.name) }
                    ?: KotlinBaseScriptingBundle.message("script.name.gradle.script.sdk.dependencies")
            } else {
                sdk?.let { KotlinBaseScriptingBundle.message("script.name.kotlin.script.sdk.dependencies.0", it.name) }
                    ?: KotlinBaseScriptingBundle.message("script.name.kotlin.script.sdk.dependencies")
            }
    }

}

fun Collection<VirtualFile>.hasGradleDependency() = any { it.name.contains("gradle") }