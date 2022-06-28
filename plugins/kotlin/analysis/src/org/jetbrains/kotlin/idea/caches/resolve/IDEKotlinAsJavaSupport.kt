// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.SmartList
import org.jetbrains.kotlin.analysis.decompiled.light.classes.DecompiledLightClassesFactory
import org.jetbrains.kotlin.analysis.decompiled.light.classes.DecompiledLightClassesFactory.getLightClassForDecompiledClassOrObject
import org.jetbrains.kotlin.analysis.decompiled.light.classes.KtLightClassForDecompiledDeclaration
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.classes.*
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.caches.lightClasses.platformMutabilityWrapper
import org.jetbrains.kotlin.idea.caches.project.*
import org.jetbrains.kotlin.idea.caches.project.IdeaModuleInfo
import org.jetbrains.kotlin.idea.decompiler.navigation.SourceNavigationHelper
import org.jetbrains.kotlin.idea.project.platform
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.idea.util.ProjectRootsUtil
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.idea.util.runReadActionInSmartMode
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.analysisContext
import org.jetbrains.kotlin.resolve.scopes.MemberScope

open class IDEKotlinAsJavaSupport(private val project: Project) : KotlinAsJavaSupport() {
    private val psiManager: PsiManager = PsiManager.getInstance(project)

    override fun getFacadeNames(packageFqName: FqName, scope: GlobalSearchScope): Collection<String> {
        val facadeFilesInPackage = project.runReadActionInSmartMode {
            KotlinFileFacadeClassByPackageIndex.get(packageFqName.asString(), project, scope)
        }

        return facadeFilesInPackage.map { it.javaFileFacadeFqName.shortName().asString() }.toSet()
    }

    override fun getFacadeClassesInPackage(packageFqName: FqName, scope: GlobalSearchScope): Collection<PsiClass> {
        val facadeFilesInPackage = runReadAction {
            KotlinFileFacadeClassByPackageIndex.get(packageFqName.asString(), project, scope).platformSourcesFirst()
        }
        val groupedByFqNameAndModuleInfo = facadeFilesInPackage.groupBy {
            Pair(it.javaFileFacadeFqName, it.getModuleInfoPreferringJvmPlatform())
        }

        return groupedByFqNameAndModuleInfo.flatMap {
            val (key, files) = it
            val (fqName, moduleInfo) = key
            createLightClassForFileFacade(fqName, files, moduleInfo)
        }
    }

    override fun findClassOrObjectDeclarations(fqName: FqName, searchScope: GlobalSearchScope): Collection<KtClassOrObject> {
        return project.runReadActionInSmartMode {
            KotlinFullClassNameIndex.get(
                fqName.asString(),
                project,
                KotlinSourceFilterScope.sourceAndClassFiles(searchScope, project)
            )
        }
    }

    override fun findFilesForPackage(fqName: FqName, searchScope: GlobalSearchScope): Collection<KtFile> {
        return project.runReadActionInSmartMode {
            PackageIndexUtil.findFilesWithExactPackage(
                fqName,
                KotlinSourceFilterScope.sourceAndClassFiles(
                    searchScope,
                    project
                ),
                project
            )
        }
    }

    override fun findClassOrObjectDeclarationsInPackage(
        packageFqName: FqName,
        searchScope: GlobalSearchScope
    ): Collection<KtClassOrObject> {
        return KotlinTopLevelClassByPackageIndex.get(
            packageFqName.asString(), project,
            KotlinSourceFilterScope.sourceAndClassFiles(searchScope, project)
        )
    }

    override fun packageExists(fqName: FqName, scope: GlobalSearchScope): Boolean {
        return PackageIndexUtil.packageExists(
            fqName,
            KotlinSourceFilterScope.sourceAndClassFiles(
                scope,
                project
            )
        )
    }

    override fun getSubPackages(fqn: FqName, scope: GlobalSearchScope): Collection<FqName> {
        return PackageIndexUtil.getSubPackageFqNames(
            fqn,
            KotlinSourceFilterScope.sourceAndClassFiles(
                scope,
                project
            ),
            MemberScope.ALL_NAME_FILTER
        )
    }

    private val recursiveGuard = ThreadLocal<Boolean>()

    private inline fun <T> guardedRun(body: () -> T): T? {
        if (recursiveGuard.get() == true) return null
        return try {
            recursiveGuard.set(true)
            body()
        } finally {
            recursiveGuard.set(false)
        }
    }

    override fun getLightClass(classOrObject: KtClassOrObject): KtLightClass? {
        if (!classOrObject.isValid) {
            return null
        }

        val virtualFile = classOrObject.containingFile.virtualFile
        if (virtualFile != null) {
            when {
                ProjectRootsUtil.isProjectSourceFile(project, virtualFile) ->
                    return KtLightClassForSourceDeclaration.create(classOrObject)
                ProjectRootsUtil.isLibraryClassFile(project, virtualFile) ->
                    return getLightClassForDecompiledClassOrObject(classOrObject, project)
                ProjectRootsUtil.isLibrarySourceFile(project, virtualFile) ->
                    return guardedRun {
                        SourceNavigationHelper.getOriginalClass(classOrObject) as? KtLightClass
                    }
            }
        }

        if ((classOrObject.containingFile as? KtFile)?.analysisContext != null ||
            classOrObject.containingFile.originalFile.virtualFile != null
        ) {
            return KtLightClassForSourceDeclaration.create(classOrObject)
        }
        return null
    }

    override fun getLightClassForScript(script: KtScript): KtLightClass? {
        if (!script.isValid) {
            return null
        }

        return KtLightClassForScript.create(script)
    }

    override fun getFacadeClasses(facadeFqName: FqName, scope: GlobalSearchScope): Collection<PsiClass> {
        val filesByModule = findFilesForFacade(facadeFqName, scope).groupBy(PsiElement::getModuleInfoPreferringJvmPlatform)

        return filesByModule.flatMap {
            createLightClassForFileFacade(facadeFqName, it.value, it.key)
        }
    }

    override fun getScriptClasses(scriptFqName: FqName, scope: GlobalSearchScope): Collection<PsiClass> {
        return KotlinScriptFqnIndex.get(scriptFqName.asString(), project, scope).mapNotNull {
            getLightClassForScript(it)
        }
    }

    override fun getKotlinInternalClasses(fqName: FqName, scope: GlobalSearchScope): Collection<PsiClass> {
        if (fqName.isRoot) return emptyList()

        val packageParts = findPackageParts(fqName, scope)
        val platformWrapper = findPlatformWrapper(fqName, scope)
        return if (platformWrapper != null) packageParts + platformWrapper else packageParts
    }

    private fun findPackageParts(fqName: FqName, scope: GlobalSearchScope): List<KtLightClassForDecompiledDeclaration> {
        val facadeKtFiles = StaticFacadeIndexUtil.getMultifileClassForPart(fqName, scope, project)
        val partShortName = fqName.shortName().asString()
        val partClassFileShortName = "$partShortName.class"

        return facadeKtFiles.mapNotNull { facadeKtFile ->
            if (facadeKtFile is KtClsFile) {
                val partClassFile = facadeKtFile.virtualFile.parent.findChild(partClassFileShortName) ?: return@mapNotNull null
                val javaClsClass =
                    DecompiledLightClassesFactory.createClsJavaClassFromVirtualFile(facadeKtFile, partClassFile, null, project)
                        ?: return@mapNotNull null
                KtLightClassForDecompiledDeclaration(javaClsClass, javaClsClass.parent, facadeKtFile, null)
            } else {
                // TODO should we build light classes for parts from source?
                null
            }
        }
    }

    private fun findPlatformWrapper(fqName: FqName, scope: GlobalSearchScope): PsiClass? {
        return platformMutabilityWrapper(fqName) {
            JavaPsiFacade.getInstance(
                project
            ).findClass(it, scope)
        }
    }

    private fun createLightClassForFileFacade(
        facadeFqName: FqName,
        facadeFiles: List<KtFile>,
        moduleInfo: IdeaModuleInfo
    ): List<PsiClass> = SmartList<PsiClass>().apply {

        tryCreateFacadesForSourceFiles(moduleInfo, facadeFqName)?.let { sourcesFacade ->
            add(sourcesFacade)
        }

        facadeFiles.filterIsInstance<KtClsFile>().mapNotNullTo(this) {
            DecompiledLightClassesFactory.createLightClassForDecompiledKotlinFile(it, project)
        }
    }

    private fun tryCreateFacadesForSourceFiles(moduleInfo: IdeaModuleInfo, facadeFqName: FqName): PsiClass? {
        if (moduleInfo !is ModuleSourceInfo && moduleInfo !is PlatformModuleInfo) return null
        return KtLightClassForFacadeImpl.createForFacade(psiManager, facadeFqName, moduleInfo.contentScope())
    }

    override fun findFilesForFacade(facadeFqName: FqName, scope: GlobalSearchScope): Collection<KtFile> {
        return runReadAction {
            KotlinFileFacadeFqNameIndex.get(facadeFqName.asString(), project, scope).platformSourcesFirst()
        }
    }

    override fun getFakeLightClass(classOrObject: KtClassOrObject): KtFakeLightClass =
        KtDescriptorBasedFakeLightClass(classOrObject)

    override fun createFacadeForSyntheticFile(facadeClassFqName: FqName, file: KtFile): PsiClass =
        KtLightClassForFacadeImpl.createForSyntheticFile(facadeClassFqName, file)

    // NOTE: this is a hacky solution to the following problem:
    // when building this light class resolver will be built by the first file in the list
    // (we could assume that files are in the same module before)
    // thus we need to ensure that resolver will be built by the file from platform part of the module
    // (resolver built by a file from the common part will have no knowledge of the platform part)
    // the actual of order of files that resolver receives is controlled by *findFilesForFacade* method
    private fun Collection<KtFile>.platformSourcesFirst() = sortedByDescending { it.platform.isJvm() }


}

internal fun PsiElement.getModuleInfoPreferringJvmPlatform(): IdeaModuleInfo {
    return getPlatformModuleInfo(JvmPlatforms.unspecifiedJvmPlatform) ?: getModuleInfo()
}
