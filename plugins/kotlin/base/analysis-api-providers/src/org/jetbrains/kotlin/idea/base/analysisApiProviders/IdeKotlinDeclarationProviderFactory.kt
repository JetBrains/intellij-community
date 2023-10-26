// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.analysisApiProviders

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndex.ValueProcessor
import org.jetbrains.kotlin.analysis.project.structure.KtBuiltinsModule
import org.jetbrains.kotlin.analysis.project.structure.KtLibrarySourceModule
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.providers.KotlinDeclarationProvider
import org.jetbrains.kotlin.analysis.providers.KotlinDeclarationProviderFactory
import org.jetbrains.kotlin.analysis.providers.KotlinDeclarationProviderMerger
import org.jetbrains.kotlin.analysis.providers.impl.declarationProviders.CompositeKotlinDeclarationProvider
import org.jetbrains.kotlin.analysis.providers.impl.declarationProviders.FileBasedKotlinDeclarationProvider
import org.jetbrains.kotlin.analysis.providers.impl.mergeSpecificProviders
import org.jetbrains.kotlin.idea.base.indices.names.KotlinBinaryRootToPackageIndex
import org.jetbrains.kotlin.idea.base.indices.names.KotlinTopLevelCallableByPackageShortNameIndex
import org.jetbrains.kotlin.idea.base.indices.names.KotlinTopLevelClassLikeDeclarationByPackageShortNameIndex
import org.jetbrains.kotlin.idea.base.indices.names.getNamesInPackage
import org.jetbrains.kotlin.idea.base.indices.names.isSupportedByBinaryRootToPackageIndex
import org.jetbrains.kotlin.idea.base.indices.processElementsAndMeasure
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal class IdeKotlinDeclarationProviderFactory(private val project: Project) : KotlinDeclarationProviderFactory() {
    override fun createDeclarationProvider(scope: GlobalSearchScope, contextualModule: KtModule?): KotlinDeclarationProvider {
        val mainProvider = IdeKotlinDeclarationProvider(project, scope, contextualModule)

        if (contextualModule is KtSourceModuleByModuleInfoForOutsider) {
            val fakeKtFile = PsiManager.getInstance(contextualModule.project).findFile(contextualModule.fakeVirtualFile)
            if (fakeKtFile is KtFile) {
                val providerForFake = FileBasedKotlinDeclarationProvider(fakeKtFile)
                return CompositeKotlinDeclarationProvider.create(listOf(providerForFake, mainProvider))
            }
        }

        return mainProvider
    }
}

internal class IdeKotlinDeclarationProviderMerger(private val project: Project) : KotlinDeclarationProviderMerger() {
    override fun merge(providers: List<KotlinDeclarationProvider>): KotlinDeclarationProvider =
        providers.mergeSpecificProviders<_, IdeKotlinDeclarationProvider>(CompositeKotlinDeclarationProvider.factory) { targetProviders ->
            IdeKotlinDeclarationProvider(
                project,
                GlobalSearchScope.union(targetProviders.map { it.scope }),
                contextualModule = null,
            )
        }
}

private class IdeKotlinDeclarationProvider(
    private val project: Project,
    val scope: GlobalSearchScope,
    private val contextualModule: KtModule?,
) : KotlinDeclarationProvider() {
    private val log = Logger.getInstance(IdeKotlinDeclarationProvider::class.java)
    private val stubIndex: StubIndex = StubIndex.getInstance()
    private val psiManager = PsiManager.getInstance(project)

    private inline fun <IndexKey : Any, reified Psi : PsiElement> firstMatchingOrNull(
        stubKey: StubIndexKey<IndexKey, Psi>,
        key: IndexKey,
        crossinline filter: (Psi) -> Boolean = { true }
    ): Psi? {
        var result: Psi? = null
        processElementsAndMeasure(stubKey, log) {
            stubIndex.processElements(stubKey, key, project, scope, Psi::class.java) { candidate ->
                ProgressManager.checkCanceled()
                if (filter(candidate)) {
                    result = candidate
                    return@processElements false // do not continue searching over PSI
                }
                return@processElements true
            }
        }
        return result
    }

    override fun getClassLikeDeclarationByClassId(classId: ClassId): KtClassLikeDeclaration? {
        val classOrObject = firstMatchingOrNull(KotlinFullClassNameIndex.indexKey, key = classId.asStringForIndexes()) { candidate ->
            candidate.getClassId() == classId
        }
        val typeAlias = getTypeAliasByClassId(classId)
        if (classOrObject != null && typeAlias != null) {
            if (scope.compare(classOrObject.containingFile.virtualFile, typeAlias.containingFile.virtualFile) < 0) {
                return typeAlias
            }
        }
        return classOrObject ?: typeAlias
    }

    override fun getAllClassesByClassId(classId: ClassId): Collection<KtClassOrObject> =
        KotlinFullClassNameIndex.getAllElements(
            classId.asStringForIndexes(),
            project,
            scope
        ) { it.getClassId() == classId }

    override fun getAllTypeAliasesByClassId(classId: ClassId): Collection<KtTypeAlias> {
        return listOfNotNull(getTypeAliasByClassId(classId)) //todo
    }

    override fun getTopLevelCallableNamesInPackage(packageFqName: FqName): Set<Name> {
        return getNamesInPackage(KotlinTopLevelCallableByPackageShortNameIndex.NAME, packageFqName, scope)
    }

    override fun getTopLevelKotlinClassLikeDeclarationNamesInPackage(packageFqName: FqName): Set<Name> {
        return getNamesInPackage(KotlinTopLevelClassLikeDeclarationByPackageShortNameIndex.NAME, packageFqName, scope)
    }

    override fun findFilesForFacadeByPackage(packageFqName: FqName): Collection<KtFile> {
        return KotlinFileFacadeClassByPackageIndex[packageFqName.asString(), project, scope]
    }

    override fun computePackageSetWithTopLevelCallableDeclarations(): Set<String>? = computePackageNames(contextualModule)

    private fun computePackageNames(module: KtModule?): Set<String>? = when (module) {
        is KtSourceModuleByModuleInfo -> computeSourceModulePackageSet(module)
        is SdkKtModuleByModuleInfo -> computeSdkModulePackageSet(module)
        is KtLibraryModuleByModuleInfo -> computeLibraryModulePackageSet(module)
        is KtLibrarySourceModule -> computePackageNames(module.binaryLibrary)
        is KtBuiltinsModule -> StandardClassIds.builtInsPackages.mapTo(mutableSetOf()) { it.asString() }
        else -> null
    }

    private fun computeSourceModulePackageSet(module: KtSourceModuleByModuleInfo): Set<String>? = null // KTIJ-27450

    private fun computeSdkModulePackageSet(module: SdkKtModuleByModuleInfo): Set<String>? =
        computePackageSetFromBinaryRoots(module.moduleInfo.sdk.rootProvider.getFiles(OrderRootType.CLASSES))

    private fun computeLibraryModulePackageSet(module: KtLibraryModuleByModuleInfo): Set<String>? =
        computePackageSetFromBinaryRoots(module.libraryInfo.library.getFiles(OrderRootType.CLASSES))

    private fun computePackageSetFromBinaryRoots(binaryRoots: Array<VirtualFile>): Set<String>? {
        if (binaryRoots.any { !it.isSupportedByBinaryRootToPackageIndex }) {
            return null
        }

        // If the `KotlinBinaryRootToPackageIndex` doesn't contain any of the (supported) binary roots, we can still return an empty set,
        // because the index is exhaustive for binary libraries. An empty set means that the library doesn't contain any Kotlin
        // declarations.
        return buildSet {
            binaryRoots.forEach { binaryRoot ->
                FileBasedIndex.getInstance().processValues<String, String>(
                    KotlinBinaryRootToPackageIndex.NAME,
                    binaryRoot.name,
                    null,
                    ValueProcessor<String> { _, packageName ->
                        add(packageName)
                        true
                    },

                    // We don't need to use the declaration provider's scope, as the binary file name is already sufficiently specific (and
                    // false positives are admissible).
                    GlobalSearchScope.allScope(project),
                )
            }
        }
    }

    override fun findFilesForFacade(facadeFqName: FqName): Collection<KtFile> {
        //TODO original LC has platformSourcesFirst()
        return KotlinFileFacadeFqNameIndex[facadeFqName.asString(), project, scope]
    }

    override fun findInternalFilesForFacade(facadeFqName: FqName): Collection<KtFile> {
        return KotlinMultiFileClassPartIndex[facadeFqName.asString(), project, scope]
    }

    override fun findFilesForScript(scriptFqName: FqName): Collection<KtScript> {
        return KotlinScriptFqnIndex[scriptFqName.asString(), project, scope]
    }

    private fun getTypeAliasByClassId(classId: ClassId): KtTypeAlias? {
        return firstMatchingOrNull(
            stubKey = KotlinTopLevelTypeAliasFqNameIndex.indexKey,
            key = classId.asStringForIndexes(),
            filter = { candidate -> candidate.getClassId() == classId }
        ) ?: firstMatchingOrNull(stubKey = KotlinInnerTypeAliasClassIdIndex.indexKey, key = classId.asString())
    }

    override fun getTopLevelProperties(callableId: CallableId): Collection<KtProperty> =
        KotlinTopLevelPropertyFqnNameIndex.get(callableId.asTopLevelStringForIndexes(), project, scope)

    override fun getTopLevelFunctions(callableId: CallableId): Collection<KtNamedFunction> =
        KotlinTopLevelFunctionFqnNameIndex.get(callableId.asTopLevelStringForIndexes(), project, scope)

    override fun getTopLevelCallableFiles(callableId: CallableId): Collection<KtFile> {
        val callableIdString = callableId.asTopLevelStringForIndexes()

        return buildSet {
            stubIndex.getContainingFilesIterator(KotlinTopLevelPropertyFqnNameIndex.indexKey, callableIdString, project, scope).forEach { file ->
                psiManager.findFile(file)?.safeAs<KtFile>()?.let { add(it) }
            }
            stubIndex.getContainingFilesIterator(KotlinTopLevelFunctionFqnNameIndex.indexKey, callableIdString, project, scope).forEach { file ->
                psiManager.findFile(file)?.safeAs<KtFile>()?.let { add(it) }
            }
        }
    }


    companion object {
        private fun CallableId.asTopLevelStringForIndexes(): String {
            require(this.classId == null) {
                "Expecting top-level callable, but was $this"
            }

            if (packageName.isRoot) return callableName.asString()
            return "${packageName.asString()}.${callableName.asString()}"
        }

        private fun ClassId.asStringForIndexes(): String {
            if (packageFqName.isRoot) return relativeClassName.asString()
            return "${packageFqName.asString()}.${relativeClassName.asString()}"
        }
    }
}