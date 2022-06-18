// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.dependencies

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import javax.swing.Icon

class KotlinScriptDependenciesLibraryRootProvider: AdditionalLibraryRootsProvider() {
    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        val manager = ScriptConfigurationManager.getInstance(project)
        val classes = manager.getAllScriptsDependenciesClassFiles().filterValid()
        val sources = manager.getAllScriptDependenciesSources().filterValid()
        return if (classes.isEmpty() && sources.isEmpty()) {
            emptyList()
        } else {
            listOf(KotlinScriptDependenciesLibrary(classes = classes, sources = sources))
        }
    }

    private fun Collection<VirtualFile>.filterValid() = this.filterTo(LinkedHashSet(), VirtualFile::isValid)

    override fun getRootsToWatch(project: Project): Collection<VirtualFile> =
        ScriptConfigurationManager.allExtraRoots(project).filterValid()

    private data class KotlinScriptDependenciesLibrary(val classes: Collection<VirtualFile>, val sources: Collection<VirtualFile>) :
        SyntheticLibrary(), ItemPresentation {

        override fun getBinaryRoots(): Collection<VirtualFile> = classes

        override fun getSourceRoots(): Collection<VirtualFile> = sources

        override fun getPresentableText(): String = KotlinBundle.message("script.name.kotlin.script.dependencies")

        override fun getIcon(unused: Boolean): Icon = KotlinIcons.SCRIPT
    }

}