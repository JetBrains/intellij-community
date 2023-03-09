// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.stubindex.resolve

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.kotlin.idea.caches.project.LibraryModificationTracker
import org.jetbrains.kotlin.idea.caches.trackers.KotlinCodeBlockModificationListener
import org.jetbrains.kotlin.idea.vfilefinder.KotlinShortClassNameFileIndex
import java.util.concurrent.ConcurrentHashMap

class ShortNamesCacheService(private val project: Project) {

    private val shortNamesCache =
        CachedValuesManager.getManager(project).createCachedValue(
            {
                CachedValueProvider.Result.create(
                    ConcurrentHashMap<String, Set<String>>(),
                    LibraryModificationTracker.getInstance(project),
                    ProjectRootModificationTracker.getInstance(project),
                    KotlinCodeBlockModificationListener.getInstance(project).kotlinOutOfCodeBlockTracker
                )
            },
            /* trackValue = */ false
        )

    fun getShortNameCandidates(name: String): Set<String> =
        shortNamesCache.value.getOrPut(name) {
            val scope = GlobalSearchScope.everythingScope(project)
            val fqNames = hashSetOf<String>()
            FileBasedIndex.getInstance().processValues(
                KotlinShortClassNameFileIndex.NAME, name, null,
                FileBasedIndex.ValueProcessor { _, names ->
                    fqNames += names
                    true
                }, scope
            )
            fqNames
        }

    companion object {
        fun getInstance(project: Project): ShortNamesCacheService = project.service()
    }
}