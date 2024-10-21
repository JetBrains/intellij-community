// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.analysisApiPlatform

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.analysis.api.platform.declarations.*
import org.jetbrains.kotlin.analysis.api.platform.mergeSpecificProviders
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinGlobalSearchScopeMerger
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.base.indices.names.KotlinTopLevelCallableByPackageShortNameIndex
import org.jetbrains.kotlin.idea.base.indices.names.KotlinTopLevelClassLikeDeclarationByPackageShortNameIndex
import org.jetbrains.kotlin.idea.base.indices.names.getNamesInPackage
import org.jetbrains.kotlin.idea.base.indices.processElementsAndMeasure
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal class IdeKotlinDeclarationProviderFactory(private val project: Project) : KotlinDeclarationProviderFactory {
    override fun createDeclarationProvider(scope: GlobalSearchScope, contextualModule: KaModule?): KotlinDeclarationProvider {
        val mainProvider = IdeKotlinDeclarationProvider(project, scope, contextualModule)

        @OptIn(K1ModeProjectStructureApi::class)
        if (contextualModule is KtSourceModuleByModuleInfoForOutsider) {
            val fakeKtFile = PsiManager.getInstance(contextualModule.project).findFile(contextualModule.fakeVirtualFile)
            if (fakeKtFile is KtFile) {
                val providerForFake = KotlinFileBasedDeclarationProvider(fakeKtFile)
                return KotlinCompositeDeclarationProvider.create(listOf(providerForFake, mainProvider))
            }
        }

        return mainProvider
    }
}

internal class IdeKotlinDeclarationProviderMerger(private val project: Project) : KotlinDeclarationProviderMerger {
    override fun merge(providers: List<KotlinDeclarationProvider>): KotlinDeclarationProvider =
        providers.mergeSpecificProviders<_, IdeKotlinDeclarationProvider>(KotlinCompositeDeclarationProvider.factory) { targetProviders ->
            IdeKotlinDeclarationProvider(
                project,
                KotlinGlobalSearchScopeMerger.getInstance(project).union(targetProviders.map { it.scope }),
                contextualModule = null,
            )
        }
}

private class IdeKotlinDeclarationProvider(
    private val project: Project,
    val scope: GlobalSearchScope,
    private val contextualModule: KaModule?,
) : KotlinDeclarationProvider {
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
            .toList()

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

    override val hasSpecificClassifierPackageNamesComputation: Boolean get() = false
    override val hasSpecificCallablePackageNamesComputation: Boolean get() = false

    override fun computePackageNames(): Set<String>? =
        contextualModule?.let { IdeKotlinModulePackageNamesProvider.getInstance(project).computePackageNames(it) }

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
                //check canceled is done inside findFile
                psiManager.findFile(file)?.safeAs<KtFile>()?.let { add(it) }
            }
            stubIndex.getContainingFilesIterator(KotlinTopLevelFunctionFqnNameIndex.indexKey, callableIdString, project, scope).forEach { file ->
                //check canceled is done inside findFile
                psiManager.findFile(file)?.safeAs<KtFile>()?.let { add(it) }
            }
        }
    }


    companion object {
        private val log = Logger.getInstance(IdeKotlinDeclarationProvider::class.java)

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