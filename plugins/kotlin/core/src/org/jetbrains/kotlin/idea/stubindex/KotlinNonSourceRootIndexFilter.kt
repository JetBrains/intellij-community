// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.find.ngrams.TrigramIndex
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.util.ThreeState
import com.intellij.util.indexing.IndexId
import com.intellij.util.indexing.IndexedFile
import com.intellij.util.indexing.hints.BaseFileTypeInputFilter
import com.intellij.util.indexing.hints.BaseGlobalFileTypeInputFilter
import com.intellij.util.indexing.hints.FileTypeSubstitutionStrategy.BEFORE_SUBSTITUTION
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations.KotlinForwardDeclarationsFileSystem

private const val KOTLIN_DOT_FILE_EXTENSION = ".${KotlinFileType.EXTENSION}"

class KotlinNonSourceRootIndexFilter : BaseGlobalFileTypeInputFilter() {
    private val enabled = !System.getProperty("kotlin.index.non.source.roots", "false").toBoolean()

    override fun getFileTypeHintForAffectedIndex(indexId: IndexId<*, *>): BaseFileTypeInputFilter {
        return object : BaseFileTypeInputFilter(BEFORE_SUBSTITUTION) {
            override fun acceptFileType(fileType: FileType): ThreeState {
                return if (fileType == KotlinFileType.INSTANCE) ThreeState.UNSURE else ThreeState.YES
            }

            override fun slowPathIfFileTypeHintUnsure(file: IndexedFile): Boolean {
                return !isExcludedFromIndex(file.file, indexId, file.project)
            }

            private fun isExcludedFromIndex(virtualFile: VirtualFile, indexId: IndexId<*, *>, project: Project?): Boolean =
                project != null &&
                        virtualFile.nameSequence.endsWith(KOTLIN_DOT_FILE_EXTENSION) && // kts is also KotlinFileType, but it should not be excluded
                        runReadAction {
                            !ProjectFileIndex.getInstance(project).isInSource(virtualFile)
                                    && !isUnderIndexablePath(virtualFile)
                        }

            private fun isUnderIndexablePath(virtualFile: VirtualFile): Boolean {
                val storageRoot = KotlinForwardDeclarationsFileSystem.storageRootPath
                val fileNormalizedPath = virtualFile.fileSystem.getNioPath(virtualFile)?.normalize()
                return fileNormalizedPath?.startsWith(storageRoot) == true
            }
        }
    }

    override fun getVersion(): Int = 0

    override fun affectsIndex(indexId: IndexId<*, *>): Boolean =
        enabled && (indexId !== TrigramIndex.INDEX_ID && indexId !== FilenameIndex.NAME)
}