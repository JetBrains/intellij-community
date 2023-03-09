// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.find.ngrams.TrigramIndex
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.util.indexing.GlobalIndexFilter
import com.intellij.util.indexing.IndexId
import org.jetbrains.kotlin.idea.KotlinFileType

class KotlinNonSourceRootIndexFilter: GlobalIndexFilter {
    private val enabled = !System.getProperty("kotlin.index.non.source.roots", "false").toBoolean()

    override fun isExcludedFromIndex(virtualFile: VirtualFile, indexId: IndexId<*, *>): Boolean = false

    override fun isExcludedFromIndex(virtualFile: VirtualFile, indexId: IndexId<*, *>, project: Project?): Boolean =
        project != null &&
                !virtualFile.isDirectory &&
                affectsIndex(indexId) &&
                virtualFile.extension == KotlinFileType.EXTENSION &&
                runReadAction {
                    ProjectRootManager.getInstance(project).fileIndex.getOrderEntriesForFile(virtualFile).isEmpty() &&
                            !ProjectFileIndex.getInstance(project).isInLibrary(virtualFile)
                }

    override fun getVersion(): Int = 0

    override fun affectsIndex(indexId: IndexId<*, *>): Boolean =
        enabled && (indexId !== TrigramIndex.INDEX_ID && indexId !== FilenameIndex.NAME)
}