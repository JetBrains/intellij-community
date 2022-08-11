// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.analysis.providers.ide

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.analysis.providers.KotlinDeclarationProvider
import org.jetbrains.kotlin.analysis.providers.KotlinDeclarationProviderFactory
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

internal class KotlinIdeDeclarationProviderFactory(private val project: Project) : KotlinDeclarationProviderFactory() {
    override fun createDeclarationProvider(searchScope: GlobalSearchScope): KotlinDeclarationProvider {
        return KotlinIdeDeclarationProvider(project, searchScope)
    }
}

private class KotlinIdeDeclarationProvider(
    private val project: Project,
    private val searchScope: GlobalSearchScope
) : KotlinDeclarationProvider() {

    private val stubIndex: StubIndex = StubIndex.getInstance()

    private inline fun <INDEX_KEY : Any, reified PSI : PsiElement> firstMatchingOrNull(
        stubKey: StubIndexKey<INDEX_KEY, PSI>,
        key: INDEX_KEY,
        crossinline filter: (PSI) -> Boolean = { true }
    ): PSI? {
        var result: PSI? = null
        stubIndex.processElements(
            stubKey, key, project, searchScope, PSI::class.java,
        ) { candidate ->
            if (filter(candidate)) {
                result = candidate
                return@processElements false // do not continue searching over PSI
            }
            true
        }
        return result
    }

    override fun getClassLikeDeclarationByClassId(classId: ClassId): KtClassLikeDeclaration? {
        return firstMatchingOrNull<String, KtClassOrObject>(
            KotlinFullClassNameIndex.KEY,
            key = classId.asStringForIndexes(),
        ) { candidate -> candidate.getClassId() == classId }
            ?: getTypeAliasByClassId(classId)
    }
    override fun getAllClassesByClassId(classId: ClassId): Collection<KtClassOrObject> {
        return KotlinFullClassNameIndex
            .get(classId.asStringForIndexes(), project, searchScope)
            .filter { candidate -> candidate.containingKtFile.packageFqName == classId.packageFqName }
    }

    override fun getAllTypeAliasesByClassId(classId: ClassId): Collection<KtTypeAlias> {
        return listOfNotNull(getTypeAliasByClassId(classId)) //todo
    }

    override fun getTypeAliasNamesInPackage(packageFqName: FqName): Set<Name> {
        return KotlinTopLevelTypeAliasByPackageIndex
            .get(packageFqName.asStringForIndexes(), project, searchScope)
            .mapNotNullTo(mutableSetOf()) { it.nameAsName }
    }

    override fun getPropertyNamesInPackage(packageFqName: FqName): Set<Name> {
        return KotlinTopLevelPropertyByPackageIndex
            .get(packageFqName.asStringForIndexes(), project, searchScope)
            .mapNotNullTo(mutableSetOf()) { it.nameAsName }
    }

    override fun getFunctionsNamesInPackage(packageFqName: FqName): Set<Name> {
        return KotlinTopLevelFunctionByPackageIndex
            .get(packageFqName.asStringForIndexes(), project, searchScope)
            .mapNotNullTo(mutableSetOf()) { it.nameAsName }
    }

    override fun findFilesForFacadeByPackage(packageFqName: FqName): Collection<KtFile> {
        return KotlinFileFacadeClassByPackageIndex.get(packageFqName.asString(), project, searchScope)
    }

    override fun findFilesForFacade(facadeFqName: FqName): Collection<KtFile> {
        return KotlinFileFacadeFqNameIndex.get(
            key = facadeFqName.asString(),
            project = project,
            scope = searchScope
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
        KotlinTopLevelPropertyFqnNameIndex.get(callableId.asStringForIndexes(), project, searchScope)

    override fun getTopLevelFunctions(callableId: CallableId): Collection<KtNamedFunction> =
        KotlinTopLevelFunctionFqnNameIndex.get(callableId.asStringForIndexes(), project, searchScope)


    override fun getClassNamesInPackage(packageFqName: FqName): Set<Name> =
        KotlinTopLevelClassByPackageIndex
            .get(packageFqName.asStringForIndexes(), project, searchScope)
            .mapNotNullTo(hashSetOf()) { it.nameAsName }

    companion object {
        private fun CallableId.asStringForIndexes(): String =
            (if (packageName.isRoot) callableName.asString() else toString()).replace('/', '.')

        private fun FqName.asStringForIndexes(): String =
            asString().replace('/', '.')

        private fun ClassId.asStringForIndexes(): String =
            asSingleFqName().asStringForIndexes()
    }
}


