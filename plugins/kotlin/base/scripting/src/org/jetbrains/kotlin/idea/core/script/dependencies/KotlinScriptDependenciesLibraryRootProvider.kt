// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.dependencies

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.scripting.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import javax.swing.Icon

class KotlinScriptDependenciesLibraryRootProvider: AdditionalLibraryRootsProvider() {
    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
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

    override fun getRootsToWatch(project: Project): Collection<VirtualFile> =
        ScriptConfigurationManager.allExtraRoots(project).filterValid()

    private data class KotlinScriptDependenciesLibrary(val classes: Collection<VirtualFile>, val sources: Collection<VirtualFile>) :
        SyntheticLibrary("KotlinScriptDependenciesLibrary", null), ItemPresentation {
        override fun getBinaryRoots(): Collection<VirtualFile> = classes

        override fun getSourceRoots(): Collection<VirtualFile> = sources

        override fun getPresentableText(): String = KotlinBaseScriptingBundle.message("script.name.kotlin.script.dependencies")

        override fun getIcon(unused: Boolean): Icon = KotlinIcons.SCRIPT
    }

    private data class ScriptSdk(val sdk: Sdk?, val classes: Collection<VirtualFile>, val sources: Collection<VirtualFile>) :
        SyntheticLibrary(), ItemPresentation {
        override fun getBinaryRoots(): Collection<VirtualFile> = classes

        override fun getSourceRoots(): Collection<VirtualFile> = sources

        override fun getPresentableText(): String =
            sdk?.let { KotlinBaseScriptingBundle.message("script.name.kotlin.script.sdk.dependencies.0", it.name) }
                ?: KotlinBaseScriptingBundle.message("script.name.kotlin.script.sdk.dependencies")

        override fun getIcon(unused: Boolean): Icon = KotlinIcons.GRADLE_SCRIPT
    }

}