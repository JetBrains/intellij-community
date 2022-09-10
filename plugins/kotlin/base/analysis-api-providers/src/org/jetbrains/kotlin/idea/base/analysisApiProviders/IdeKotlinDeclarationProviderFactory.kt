// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.analysisApiProviders

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.analysis.providers.KotlinDeclarationProvider
import org.jetbrains.kotlin.analysis.providers.KotlinDeclarationProviderFactory
import org.jetbrains.kotlin.idea.base.indices.names.KotlinTopLevelCallableByPackageShortNameIndex
import org.jetbrains.kotlin.idea.base.indices.names.KotlinTopLevelClassLikeDeclarationByPackageShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

internal class IdeKotlinDeclarationProviderFactory(private val project: Project) : KotlinDeclarationProviderFactory() {
    override fun createDeclarationProvider(searchScope: GlobalSearchScope): KotlinDeclarationProvider {
        return IdeKotlinDeclarationProvider(project, searchScope)
    }
}

private class IdeKotlinDeclarationProvider(
    private val project: Project,
    private val scope: GlobalSearchScope
) : KotlinDeclarationProvider() {
    private val stubIndex: StubIndex = StubIndex.getInstance()

    private inline fun <IndexKey : Any, reified Psi : PsiElement> firstMatchingOrNull(
        stubKey: StubIndexKey<IndexKey, Psi>,
        key: IndexKey,
        crossinline filter: (Psi) -> Boolean = { true }
    ): Psi? {
        var result: Psi? = null
        stubIndex.processElements(stubKey, key, project, scope, Psi::class.java) { candidate ->
            if (filter(candidate)) {
                result = candidate
                return@processElements false // do not continue searching over PSI
            }
            return@processElements true
        }
        return result
    }

    override fun getClassLikeDeclarationByClassId(classId: ClassId): KtClassLikeDeclaration? {
        return firstMatchingOrNull(KotlinFullClassNameIndex.KEY, key = classId.asStringForIndexes()) { candidate ->
            candidate.getClassId() == classId
        } ?: getTypeAliasByClassId(classId)
    }

    override fun getAllClassesByClassId(classId: ClassId): Collection<KtClassOrObject> {
        return KotlinFullClassNameIndex
            .get(classId.asStringForIndexes(), project, scope)
            .filter { candidate -> candidate.containingKtFile.packageFqName == classId.packageFqName }
    }

    override fun getAllTypeAliasesByClassId(classId: ClassId): Collection<KtTypeAlias> {
        return listOfNotNull(getTypeAliasByClassId(classId)) //todo
    }

    override fun getTopLevelCallableNamesInPackage(packageFqName: FqName): Set<Name> {
        return KotlinTopLevelCallableByPackageShortNameIndex.getNamesInPackage(packageFqName, scope)

    }

    override fun getTopLevelKotlinClassLikeDeclarationNamesInPackage(packageFqName: FqName): Set<Name> {
        return KotlinTopLevelClassLikeDeclarationByPackageShortNameIndex.getNamesInPackage(packageFqName, scope)
    }

    override fun findFilesForFacadeByPackage(packageFqName: FqName): Collection<KtFile> {
        return KotlinFileFacadeClassByPackageIndex.get(packageFqName.asString(), project, scope)
    }

    override fun findFilesForFacade(facadeFqName: FqName): Collection<KtFile> {
        return KotlinFileFacadeFqNameIndex.get(
            key = facadeFqName.asString(),
            project = project,
            scope = scope
        ) //TODO original LC has platformSourcesFirst()
    }

    private fun getTypeAliasByClassId(classId: ClassId): KtTypeAlias? {
        return firstMatchingOrNull(
            stubKey = KotlinTopLevelTypeAliasFqNameIndex.KEY,
            key = classId.asStringForIndexes(),
            filter = { candidate -> candidate.getClassId() == classId }
        ) ?: firstMatchingOrNull(stubKey = KotlinInnerTypeAliasClassIdIndex.key, key = classId.asString())
    }

    override fun getTopLevelProperties(callableId: CallableId): Collection<KtProperty> =
        KotlinTopLevelPropertyFqnNameIndex.get(callableId.asStringForIndexes(), project, scope)

    override fun getTopLevelFunctions(callableId: CallableId): Collection<KtNamedFunction> =
        KotlinTopLevelFunctionFqnNameIndex.get(callableId.asStringForIndexes(), project, scope)

    companion object {
        private fun CallableId.asStringForIndexes(): String =
            (if (packageName.isRoot) callableName.asString() else toString()).replace('/', '.')

        private fun FqName.asStringForIndexes(): String =
            asString().replace('/', '.')

        private fun ClassId.asStringForIndexes(): String =
            asSingleFqName().asStringForIndexes()
    }
}