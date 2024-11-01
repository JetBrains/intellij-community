// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.modification.createAllLibrariesModificationTracker
import org.jetbrains.kotlin.analysis.api.platform.modification.createProjectWideOutOfBlockModificationTracker
import org.jetbrains.kotlin.analysis.decompiled.light.classes.DecompiledLightClassesFactory
import org.jetbrains.kotlin.analysis.decompiled.light.classes.DecompiledLightClassesFactory.getLightClassForDecompiledClassOrObject
import org.jetbrains.kotlin.analysis.decompiled.light.classes.KtLightClassForDecompiledDeclaration
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupportBase
import org.jetbrains.kotlin.asJava.LightClassGenerationSupport
import org.jetbrains.kotlin.asJava.classes.KtDescriptorBasedFakeLightClass
import org.jetbrains.kotlin.asJava.classes.KtFakeLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.idea.base.facet.JvmOnlyProjectChecker
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.indices.KotlinPackageIndexUtils
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.base.util.runReadActionInSmartMode
import org.jetbrains.kotlin.idea.caches.lightClasses.platformMutabilityWrapper
import org.jetbrains.kotlin.idea.caches.project.getPlatformModuleInfo
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.resolve.scopes.MemberScope

class IDEKotlinAsJavaSupport(project: Project) : KotlinAsJavaSupportBase<IdeaModuleInfo>(project) {
    override fun findClassOrObjectDeclarations(fqName: FqName, searchScope: GlobalSearchScope): Collection<KtClassOrObject> {
        val scope = KotlinSourceFilterScope.projectSourcesAndLibraryClasses(searchScope, project)
        return project.runReadActionInSmartMode {
            KotlinFullClassNameIndex[fqName.asString(), project, scope]
        }
    }

    override fun findFilesForPackage(packageFqName: FqName, searchScope: GlobalSearchScope): Collection<KtFile> =
        project.runReadActionInSmartMode {
            KotlinPackageIndexUtils.findFilesWithExactPackage(
                packageFqName,
                KotlinSourceFilterScope.projectSourcesAndLibraryClasses(
                    searchScope,
                    project
                ),
                project
            )
        }

    override fun findFilesForFacadeByPackage(packageFqName: FqName, searchScope: GlobalSearchScope): Collection<KtFile> =
        runReadAction {
            KotlinFileFacadeClassByPackageIndex[packageFqName.asString(), project, searchScope].platformSourcesFirst()
        }

    override fun findClassOrObjectDeclarationsInPackage(
        packageFqName: FqName,
        searchScope: GlobalSearchScope
    ): Collection<KtClassOrObject> {
        val scope = KotlinSourceFilterScope.projectSourcesAndLibraryClasses(searchScope, project)
        return KotlinTopLevelClassByPackageIndex[packageFqName.asString(), project, scope]
    }

    override fun packageExists(fqName: FqName, scope: GlobalSearchScope): Boolean =
        KotlinPackageIndexUtils.packageExists(
            fqName,
            KotlinSourceFilterScope.projectSourcesAndLibraryClasses(scope, project),
        )

    override fun KtFile.findModule(): IdeaModuleInfo = getPlatformModuleInfo(JvmPlatforms.unspecifiedJvmPlatform) ?: moduleInfo

    override fun facadeIsApplicable(module: IdeaModuleInfo, file: KtFile): Boolean = when (module) {
        is ModuleSourceInfo, is PlatformModuleInfo -> true
        is LibrarySourceInfo, is SdkInfo -> file.isCompiled
        else -> false
    }

    override fun projectWideOutOfBlockModificationTracker(): ModificationTracker = project.createProjectWideOutOfBlockModificationTracker()
    override fun librariesTracker(element: PsiElement): ModificationTracker = project.createAllLibrariesModificationTracker()

    override fun getSubPackages(fqn: FqName, scope: GlobalSearchScope): Collection<FqName> =
        KotlinPackageIndexUtils.getSubPackageFqNames(
            fqn,
            KotlinSourceFilterScope.projectSourcesAndLibraryClasses(scope, project),
            MemberScope.ALL_NAME_FILTER,
        )

    override fun createInstanceOfLightClass(classOrObject: KtClassOrObject): KtLightClass =
        LightClassGenerationSupport.getInstance(project).createUltraLightClass(classOrObject)

    override fun createInstanceOfDecompiledLightClass(classOrObject: KtClassOrObject): KtLightClass? =
        getLightClassForDecompiledClassOrObject(classOrObject, project)

    override fun declarationLocation(file: KtFile): DeclarationLocation? {
        val virtualFile = file.virtualFile ?: return null
        return when {
            RootKindFilter.projectSources.matches(project, virtualFile) -> DeclarationLocation.ProjectSources
            RootKindFilter.libraryClasses.matches(project, virtualFile) -> DeclarationLocation.LibraryClasses
            RootKindFilter.librarySources.matches(project, virtualFile) -> DeclarationLocation.LibrarySources
            else -> null
        }
    }

    override fun createInstanceOfLightScript(script: KtScript): KtLightClass =
        LightClassGenerationSupport.getInstance(project).createUltraLightClassForScript(script)

    override fun getScriptClasses(scriptFqName: FqName, scope: GlobalSearchScope): Collection<PsiClass> =
        KotlinScriptFqnIndex[scriptFqName.asString(), project, scope]
            .mapNotNull(::getLightClassForScript)

    override fun getKotlinInternalClasses(fqName: FqName, scope: GlobalSearchScope): Collection<PsiClass> {
        if (fqName.isRoot) return emptyList()

        val packageParts = findPackageParts(fqName, scope)
        val platformWrapper = findPlatformWrapper(fqName, scope)
        return if (platformWrapper != null) packageParts + platformWrapper else packageParts
    }

    private fun findPackageParts(fqName: FqName, scope: GlobalSearchScope): List<KtLightClassForDecompiledDeclaration> {
        val facadeKtFiles = runReadAction { KotlinMultiFileClassPartIndex[fqName.asString(), project, scope] }
        val partShortName = fqName.shortName().asString()
        val partClassFileShortName = "$partShortName.class"

        return facadeKtFiles.mapNotNull { facadeKtFile ->
            if (facadeKtFile is KtClsFile) {
                val partClassFile = facadeKtFile.virtualFile.parent.findChild(partClassFileShortName) ?: return@mapNotNull null
                val psiFile = PsiManager.getInstance(project).findFile(partClassFile) as? KtClsFile ?: facadeKtFile
                val javaClsClass =
                    DecompiledLightClassesFactory.createClsJavaClassFromVirtualFile(psiFile, partClassFile, null, project)
                        ?: return@mapNotNull null
                KtLightClassForDecompiledDeclaration(javaClsClass, javaClsClass.parent, psiFile, null)
            } else {
                // TODO should we build light classes for parts from source?
                null
            }
        }
    }

    private fun findPlatformWrapper(fqName: FqName, scope: GlobalSearchScope): PsiClass? = platformMutabilityWrapper(fqName) {
        JavaPsiFacade.getInstance(project).findClass(it, scope)
    }

    override fun findFilesForFacade(facadeFqName: FqName, searchScope: GlobalSearchScope): Collection<KtFile> {
        return KotlinFileFacadeFqNameIndex[facadeFqName.asString(), project, searchScope].platformSourcesFirst()
    }

    override fun findFilesForScript(scriptFqName: FqName, searchScope: GlobalSearchScope): Collection<KtScript> {
        return KotlinScriptFqnIndex[scriptFqName.asString(), project, searchScope]
    }

    override fun getFakeLightClass(classOrObject: KtClassOrObject): KtFakeLightClass = KtDescriptorBasedFakeLightClass(classOrObject)
    override val IdeaModuleInfo.contentSearchScope: GlobalSearchScope get() = this.contentScope

    override fun createInstanceOfLightFacade(facadeFqName: FqName, files: List<KtFile>): KtLightClassForFacade {
        return LightClassGenerationSupport.getInstance(project).createUltraLightClassForFacade(facadeFqName, files)
    }

    override fun createInstanceOfDecompiledLightFacade(facadeFqName: FqName, files: List<KtFile>): KtLightClassForFacade? {
        return DecompiledLightClassesFactory.createLightFacadeForDecompiledKotlinFile(project, facadeFqName, files)
    }

    // NOTE: this is a hacky solution to the following problem:
    // when building this light class resolver will be built by the first file in the list
    // (we could assume that files are in the same module before)
    // thus we need to ensure that resolver will be built by the file from platform part of the module
    // (resolver built by a file from the common part will have no knowledge of the platform part)
    // the actual of order of files that resolver receives is controlled by *findFilesForFacade* method
    private fun Collection<KtFile>.platformSourcesFirst(): Collection<KtFile> {
        return if (JvmOnlyProjectChecker.getInstance(project).value()) this else sortedByDescending { it.platform.isJvm() }
    }
}
