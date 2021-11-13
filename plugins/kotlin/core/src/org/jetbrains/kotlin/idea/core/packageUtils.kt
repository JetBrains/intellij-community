// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core

import com.intellij.ide.util.DirectoryChooserUtil
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemContentRootContributor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModulePackageIndex
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.util.Query
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.idea.caches.PerModulePackageCacheService
import org.jetbrains.kotlin.idea.caches.project.SourceType
import org.jetbrains.kotlin.idea.caches.project.sourceType
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.roots.invalidateProjectRoots
import org.jetbrains.kotlin.idea.util.rootManager
import org.jetbrains.kotlin.idea.util.sourceRoot
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

fun PsiDirectory.getPackage(): PsiPackage? = JavaDirectoryService.getInstance()!!.getPackage(this)

private fun PsiDirectory.getNonRootFqNameOrNull(): FqName? = getPackage()?.qualifiedName?.let(::FqName)

fun PsiFile.getFqNameByDirectory(): FqName = parent?.getNonRootFqNameOrNull() ?: FqName.ROOT

fun PsiDirectory.getFqNameWithImplicitPrefix(): FqName? {
    val packageFqName = getNonRootFqNameOrNull() ?: return null
    sourceRoot?.takeIf { !it.hasExplicitPackagePrefix(project) }?.let { sourceRoot ->
        val implicitPrefix = PerModulePackageCacheService.getInstance(project).getImplicitPackagePrefix(sourceRoot)
        return FqName.fromSegments((implicitPrefix.pathSegments() + packageFqName.pathSegments()).map { it.asString() })
    }

    return packageFqName
}

fun PsiDirectory.getFqNameWithImplicitPrefixOrRoot(): FqName = getFqNameWithImplicitPrefix() ?: FqName.ROOT

private fun VirtualFile.hasExplicitPackagePrefix(project: Project): Boolean =
    toPsiDirectory(project)?.getPackage()?.qualifiedName?.isNotEmpty() == true

fun KtFile.packageMatchesDirectory(): Boolean = packageFqName == getFqNameByDirectory()

fun KtFile.packageMatchesDirectoryOrImplicit() =
    packageFqName == getFqNameByDirectory() || packageFqName == parent?.getFqNameWithImplicitPrefix()

private fun getWritableModuleDirectory(vFiles: Query<VirtualFile>, module: Module, manager: PsiManager): PsiDirectory? {
    for (vFile in vFiles) {
        if (ModuleUtil.findModuleForFile(vFile, module.project) !== module) continue
        val directory = manager.findDirectory(vFile)
        if (directory != null && directory.isValid && directory.isWritable) {
            return directory
        }
    }
    return null
}

private fun findLongestExistingPackage(
    module: Module,
    packageName: String,
    pureKotlinSourceFolders: PureKotlinSourceFoldersHolder,
    allowedScope: GlobalSearchScope,
): PsiPackage? {
    val manager = PsiManager.getInstance(module.project)

    var nameToMatch = packageName
    while (true) {
        val vFiles = ModulePackageIndex.getInstance(module).getDirsByPackageName(nameToMatch, false).filtering(allowedScope::contains)
        val directory = getWritableModuleDirectory(vFiles, module, manager)
        if (directory != null && pureKotlinSourceFolders.hasPurePrefixInPath(module, directory.virtualFile.path)) {
            return directory.getPackage()
        }

        val lastDotIndex = nameToMatch.lastIndexOf('.')
        if (lastDotIndex < 0) {
            return null
        }

        nameToMatch = nameToMatch.substring(0, lastDotIndex)
    }
}

private val kotlinSourceRootTypes: Set<JpsModuleSourceRootType<JavaSourceRootProperties>> =
    setOf(SourceKotlinRootType, TestSourceKotlinRootType) + JavaModuleSourceRootTypes.SOURCES

private class PureKotlinSourceFoldersHolder {
    private val moduleMap = mutableMapOf<Module, Collection<String>?>()

    /***
     * @return true if `pureKotlinSourceFolders` is empty or [path] starts with any pure folder
     */
    fun hasPurePrefixInPath(module: Module, path: String): Boolean {
        val pureFolders = moduleMap.getOrPut(module) {
            KotlinFacet.get(module)?.configuration?.settings?.pureKotlinSourceFolders?.takeIf { it.isNotEmpty() && !module.isAndroidModule() }
        } ?: return true

        return pureFolders.any { path.startsWith(it, ignoreCase = true) }
    }

    fun hasPurePrefixInVirtualFile(project: Project, file: VirtualFile): Boolean {
        val moduleForFile = ModuleUtilCore.findModuleForFile(file, project) ?: return false
        return hasPurePrefixInPath(moduleForFile, file.path)
    }
}

private fun Module.getNonGeneratedKotlinSourceRoots(pureKotlinSourceFoldersHolder: PureKotlinSourceFoldersHolder): List<VirtualFile> {
    val result = mutableListOf<VirtualFile>()
    val rootManager = ModuleRootManager.getInstance(this)
    for (contentEntry in rootManager.contentEntries) {
        val sourceFolders = contentEntry.getSourceFolders(kotlinSourceRootTypes)
        for (sourceFolder in sourceFolders) {
            if (sourceFolder.jpsElement.getProperties(kotlinSourceRootTypes)?.isForGeneratedSources == true) {
                continue
            }

            sourceFolder.file?.takeIf { pureKotlinSourceFoldersHolder.hasPurePrefixInVirtualFile(project, it) }?.let(result::plusAssign)
        }
    }

    return result
}

private fun Module.getOrConfigureKotlinSourceRoots(pureKotlinSourceFoldersHolder: PureKotlinSourceFoldersHolder): List<VirtualFile> {
    val sourceRoots = getNonGeneratedKotlinSourceRoots(pureKotlinSourceFoldersHolder)
    if (sourceRoots.isNotEmpty()) {
        return sourceRoots
    }

    return listOfNotNull(createSourceRootDirectory())
}

private fun Module.createSourceRootDirectory(): VirtualFile? {
    val contentEntriesPaths = rootManager.contentEntries.mapNotNull { contentEntry ->
        contentEntry.file?.let { it.fileSystem.getNioPath(it) } ?: VfsUtilCore.convertToURL(contentEntry.url)?.path?.let(::Path)
    }

    val srcFolderPath = findSourceRootPathByExternalProject(contentEntriesPaths) ?: chooseSourceRootPathHeuristically(contentEntriesPaths)

    runWriteAction {
        VfsUtil.createDirectoryIfMissing(srcFolderPath.absolutePathString())
        project.invalidateProjectRoots()
    }

    return VfsUtil.findFile(srcFolderPath, true)
}

private fun Module.findSourceRootPathByExternalProject(allowedPaths: List<Path>): Path? = findContentRootsByExternalProject()
    ?.firstNotNullOfOrNull { externalContentRoot ->
        val path = Path(externalContentRoot.path)
        path.takeIf { allowedPaths.any { path.startsWith(it) } }
    }

private fun Module.findContentRootsByExternalProject(): Collection<ExternalSystemContentRootContributor.ExternalContentRoot>? {
    val sourceRootTypes = when (sourceType?.takeUnless { isAndroidModule() }) {
        null -> listOf(ExternalSystemSourceType.SOURCE, ExternalSystemSourceType.TEST)
        SourceType.PRODUCTION -> listOf(ExternalSystemSourceType.SOURCE)
        SourceType.TEST -> listOf(ExternalSystemSourceType.TEST)
    }

    return ExternalSystemApiUtil.getExternalProjectContentRoots(this, sourceRootTypes)
}

private fun Module.chooseSourceRootPathHeuristically(contentEntries: List<Path>): Path {
    val moduleName = name.takeLastWhile { it != '.' }
    val sourceRootPath = contentEntries.find { it.name == moduleName }
        ?: contentEntries.firstOrNull()
        ?: throw KotlinExceptionWithAttachments("Content entry path is not found").withAttachment("module", name)

    return sourceRootPath.resolve("kotlin")
}

// This is Kotlin version of PackageUtil.findOrCreateDirectoryForPackage
fun findOrCreateDirectoryForPackage(module: Module, packageName: String): PsiDirectory? {
    val project = module.project
    val pureKotlinSourceFoldersHolder = PureKotlinSourceFoldersHolder()
    var existingDirectoryByPackage: PsiDirectory? = null
    var restOfName = packageName

    if (packageName.isNotEmpty()) {
        val sourcePaths = module.getNonGeneratedKotlinSourceRoots(pureKotlinSourceFoldersHolder)
        if (sourcePaths.isNotEmpty()) {
            val allowedScope = sourcePaths.map { GlobalSearchScopesCore.DirectoryScope(project, it, true) }.reduce(GlobalSearchScope::union)
            val rootPackage = findLongestExistingPackage(module, packageName, pureKotlinSourceFoldersHolder, allowedScope)
            if (rootPackage != null) {
                val beginIndex = rootPackage.qualifiedName.length + 1
                val subPackageName = if (beginIndex < packageName.length) packageName.substring(beginIndex) else ""
                var postfixToShow = subPackageName.replace('.', File.separatorChar)
                if (subPackageName.isNotEmpty()) {
                    postfixToShow = File.separatorChar + postfixToShow
                }

                val moduleDirectories = rootPackage.getDirectories(allowedScope)
                val result = mutableListOf<PsiDirectory>()
                for (directory in moduleDirectories) {
                    if (!pureKotlinSourceFoldersHolder.hasPurePrefixInVirtualFile(project, directory.virtualFile)) continue
                    result += directory
                }

                existingDirectoryByPackage = DirectoryChooserUtil.selectDirectory(
                    project,
                    result.toTypedArray(),
                    null,
                    postfixToShow,
                ) ?: return null

                restOfName = subPackageName
            }
        }
    }

    val existingDirectory = existingDirectoryByPackage ?: run {
        val sourceRoots = module.getOrConfigureKotlinSourceRoots(pureKotlinSourceFoldersHolder)
        if (sourceRoots.isEmpty()) {
            return null
        }

        val directoryList = mutableListOf<PsiDirectory>()
        for (sourceRoot in sourceRoots) {
            val directory = PsiManager.getInstance(project).findDirectory(sourceRoot) ?: continue
            directoryList += directory
        }

        val sourceDirectories = directoryList.sortedBy { it.name }.toTypedArray()
        DirectoryChooserUtil.selectDirectory(
            project, sourceDirectories, null,
            File.separatorChar + packageName.replace('.', File.separatorChar)
        ) ?: return null
    }

    fun getLeftPart(packageName: String): String {
        val index = packageName.indexOf('.')
        return if (index > -1) packageName.substring(0, index) else packageName
    }

    fun cutLeftPart(packageName: String): String {
        val index = packageName.indexOf('.')
        return if (index > -1) packageName.substring(index + 1) else ""
    }

    var psiDirectory = existingDirectory
    while (restOfName.isNotEmpty()) {
        val name = getLeftPart(restOfName)
        val foundExistingDirectory = psiDirectory.findSubdirectory(name)
        psiDirectory = foundExistingDirectory ?: WriteAction.compute<PsiDirectory, Exception> { psiDirectory.createSubdirectory(name) }
        restOfName = cutLeftPart(restOfName)
    }
    return psiDirectory
}
