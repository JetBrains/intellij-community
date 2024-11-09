// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.stubindex.resolve

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.base.indices.KotlinPackageIndexUtils
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.caches.PerModulePackageCacheService
import org.jetbrains.kotlin.idea.caches.project.projectSourceModules
import org.jetbrains.kotlin.idea.caches.trackers.KotlinCodeBlockModificationListener
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.lazy.data.KtClassLikeInfo
import org.jetbrains.kotlin.resolve.lazy.declarations.*
import org.jetbrains.kotlin.storage.StorageManager

class PluginDeclarationProviderFactory(
  private val project: Project,
  private val indexedFilesScope: GlobalSearchScope,
  private val storageManager: StorageManager,
  private val nonIndexedFiles: Collection<KtFile>,
  private val moduleInfo: ModuleInfo
) : AbstractDeclarationProviderFactory(storageManager) {
    private val fileBasedDeclarationProviderFactory = FileBasedDeclarationProviderFactory(storageManager, nonIndexedFiles)

    override fun getClassMemberDeclarationProvider(classLikeInfo: KtClassLikeInfo): ClassMemberDeclarationProvider {
        return PsiBasedClassMemberDeclarationProvider(storageManager, classLikeInfo)
    }

    @ApiStatus.Internal
    public override fun createPackageMemberDeclarationProvider(name: FqName): PackageMemberDeclarationProvider? {
        val fileBasedProvider = fileBasedDeclarationProviderFactory.getPackageMemberDeclarationProvider(name)
        val stubBasedProvider = getStubBasedPackageMemberDeclarationProvider(name)
        return when {
            fileBasedProvider == null && stubBasedProvider == null -> null
            fileBasedProvider == null -> stubBasedProvider
            stubBasedProvider == null -> fileBasedProvider
            else -> CombinedPackageMemberDeclarationProvider(listOf(stubBasedProvider, fileBasedProvider))
        }
    }

    override fun packageExists(fqName: FqName) =
        fileBasedDeclarationProviderFactory.packageExists(fqName) || stubBasedPackageExists(fqName)

    private fun stubBasedPackageExists(name: FqName): Boolean {
        // We're only looking for source-based declarations
        return (moduleInfo as? IdeaModuleInfo)?.projectSourceModules()
            ?.any { PerModulePackageCacheService.getInstance(project).packageExists(name, it) }
            ?: false
    }

    private fun getStubBasedPackageMemberDeclarationProvider(name: FqName): PackageMemberDeclarationProvider? {
        if (!stubBasedPackageExists(name)) return null

        return StubBasedPackageMemberDeclarationProvider(name, project, indexedFilesScope)
    }

    private fun diagnoseMissingPackageFragmentPartialPackageIndexCorruption(message: String): Nothing {
        PerModulePackageCacheService.getInstance(project).onTooComplexChange() // force cache clean up
        throw InconsistencyIndexException("KotlinPartialPackageNamesIndex inconsistency.\n$message")
    }

    private fun diagnoseMissingPackageFragmentPerModulePackageCacheMiss(message: String): Nothing {
        PerModulePackageCacheService.getInstance(project).onTooComplexChange() // Postpone cache rebuild
        throw InconsistencyIndexException("PerModulePackageCache miss.\n$message")
    }

    private fun diagnoseMissingPackageFragmentUnknownReason(message: String): Nothing {
        throw IllegalStateException(message)
    }

    override fun diagnoseMissingPackageFragment(fqName: FqName, file: KtFile?) {
        if (file != null && DumbService.isDumb(file.project) &&
            FileBasedIndex.getInstance().currentDumbModeAccessType != null) {
            throw IndexNotReadyException.create()
        }
        val moduleSourceInfo = moduleInfo as? ModuleSourceInfo
        val packageExists = KotlinPackageIndexUtils.packageExists(fqName, indexedFilesScope)
        val spiPackageExists = KotlinPackageIndexUtils.packageExists(fqName, project)
        val oldPackageExists = oldPackageExists(fqName)
        val cachedPackageExists =
            moduleSourceInfo?.let { project.service<PerModulePackageCacheService>().packageExists(fqName, it) }
        val moduleModificationCount = moduleSourceInfo?.createModificationTracker()?.modificationCount

        val common = """
                packageExists = $packageExists, cachedPackageExists = $cachedPackageExists,
                oldPackageExists = $oldPackageExists,
                SPI.packageExists = $spiPackageExists,
                OOCB count = ${KotlinCodeBlockModificationListener.getInstance(project).kotlinOutOfCodeBlockTracker.modificationCount}
                moduleModificationCount = $moduleModificationCount
            """.trimIndent()

        val message = if (file != null) {
            val virtualFile = file.virtualFile
            val inScope = virtualFile in indexedFilesScope
            val packageFqName = file.packageFqName
            """
                |Cannot find package fragment '$fqName' for file ${file.name}, file package = '$packageFqName':
                |vFile: $virtualFile,
                |nonIndexedFiles = $nonIndexedFiles, isNonIndexed = ${file in nonIndexedFiles},
                |scope = $indexedFilesScope, isInScope = $inScope,
                |$common,
                |packageFqNameByTree = '${file.packageFqNameByTree}', packageDirectiveText = '${file.packageDirective?.text}'
            """.trimMargin()
        } else {
            """
                |Cannot find package fragment '$fqName' for unspecified file:
                |nonIndexedFiles = $nonIndexedFiles,
                |scope = $indexedFilesScope,
                |$common
            """.trimMargin()
        }

        val scopeNotEmptyAndContainsFile =
          !GlobalSearchScope.isEmptyScope(indexedFilesScope) && (file == null || file.virtualFile in indexedFilesScope)
        when {
            scopeNotEmptyAndContainsFile
                    && !packageExists && !oldPackageExists -> diagnoseMissingPackageFragmentPartialPackageIndexCorruption(message)

            scopeNotEmptyAndContainsFile
                    && packageExists && cachedPackageExists == false -> diagnoseMissingPackageFragmentPerModulePackageCacheMiss(message)

            else -> diagnoseMissingPackageFragmentUnknownReason(message)
        }
    }

    // trying to diagnose org.jetbrains.kotlin.resolve.lazy.NoDescriptorForDeclarationException in completion
    private val onCreationDebugInfo = debugInfo()

    fun debugToString(): String {
        return arrayOf("PluginDeclarationProviderFactory", "On failure:", debugInfo(), "On creation:", onCreationDebugInfo,
                      "moduleInfo:$moduleInfo.name", "moduleInfo dependencies: ${moduleInfo.dependencies()}").joinToString("\n")
    }

    private fun oldPackageExists(packageFqName: FqName): Boolean =
        KotlinPackageIndexUtils.packageExists(packageFqName, indexedFilesScope)

    private fun debugInfo(): String {
        if (nonIndexedFiles.isEmpty()) return "-no synthetic files-\n"

        return buildString {
            nonIndexedFiles.forEach {
                append(it.name)
                append(" isPhysical=${it.isPhysical}")
                append(" modStamp=${it.modificationStamp}")
                appendLine()
            }
        }
    }
}

private class InconsistencyIndexException(message: String): ProcessCanceledException(message)