// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.vfilefinder

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import org.jetbrains.kotlin.analysis.api.platform.restrictedAnalysis.KotlinRestrictedAnalysisService
import org.jetbrains.kotlin.analysis.api.platform.restrictedAnalysis.withRestrictedDataAccess
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinder
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import java.io.FileNotFoundException
import java.io.InputStream

class IdeVirtualFileFinder(private val scope: GlobalSearchScope, private val project: Project) : VirtualFileFinder() {
    private val restrictedAnalysisService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        KotlinRestrictedAnalysisService.getInstance(project)
    }

    override fun findMetadata(classId: ClassId): InputStream? {
        val file = findVirtualFileWithHeader(classId.asSingleFqName(), KotlinMetadataFileIndex.NAME)?.takeIf { it.exists() } ?: return null

        return try {
            file.inputStream
        } catch (_: FileNotFoundException) {
            null
        }
    }

    override fun findMetadataTopLevelClassesInPackage(packageFqName: FqName): Set<String>? = null

    override fun hasMetadataPackage(fqName: FqName): Boolean = hasSomethingInPackage(KotlinMetadataFilePackageIndex.NAME, fqName, scope)

    override fun findBuiltInsData(packageFqName: FqName): InputStream? =
        findVirtualFileWithHeader(packageFqName, KotlinBuiltInsMetadataIndex.NAME)?.inputStream

    override fun findSourceOrBinaryVirtualFile(classId: ClassId): VirtualFile? = findVirtualFileWithHeader(classId)

    init {
        if (scope != GlobalSearchScope.EMPTY_SCOPE && scope.project == null) {
            LOG.info("Scope with null project $scope")
        }
    }

    override fun findVirtualFileWithHeader(classId: ClassId): VirtualFile? =
        findVirtualFileWithHeader(classId.asSingleFqName(), KotlinClassFileIndex.NAME)

    private fun findVirtualFileWithHeader(fqName: FqName, key: ID<FqName, Void>): VirtualFile? =
        restrictedAnalysisService.withRestrictedDataAccess {
            val iterator = FileBasedIndex.getInstance().getContainingFilesIterator(key, fqName, scope)
            if (iterator.hasNext()) {
                iterator.next()
            } else {
                null
            }
        }

    companion object {
        private val LOG = Logger.getInstance(IdeVirtualFileFinder::class.java)
    }
}
