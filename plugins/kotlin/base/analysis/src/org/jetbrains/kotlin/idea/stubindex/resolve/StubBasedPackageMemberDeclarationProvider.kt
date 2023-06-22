// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex.resolve

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.CommonProcessors
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.indices.KotlinPackageIndexUtils
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode
import org.jetbrains.kotlin.idea.vfilefinder.KotlinPackageSourcesMemberNamesIndex
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.safeNameForLazyResolve
import org.jetbrains.kotlin.resolve.lazy.data.KtClassInfoUtil
import org.jetbrains.kotlin.resolve.lazy.data.KtClassOrObjectInfo
import org.jetbrains.kotlin.resolve.lazy.data.KtScriptInfo
import org.jetbrains.kotlin.resolve.lazy.declarations.PackageMemberDeclarationProvider
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter

private val isShortNameFilteringEnabled: Boolean by lazy { Registry.`is`("kotlin.indices.short.names.filtering.enabled") }

class StubBasedPackageMemberDeclarationProvider(
    private val fqName: FqName,
    private val project: Project,
    private val searchScope: GlobalSearchScope
) : PackageMemberDeclarationProvider {

    override fun getDeclarations(kindFilter: DescriptorKindFilter, nameFilter: (Name) -> Boolean): List<KtDeclaration> {
        val fqNameAsString = fqName.asString()
        val result = ArrayList<KtDeclaration>()

        fun addFromIndex(helper: KotlinStringStubIndexHelper<out KtNamedDeclaration>) {
            helper.processElements(fqNameAsString, project, searchScope) {
                if (nameFilter(it.nameAsSafeName)) {
                    result.add(it)
                }
                true
            }
        }

        if (kindFilter.acceptsKinds(DescriptorKindFilter.CLASSIFIERS_MASK)) {
            addFromIndex(KotlinTopLevelClassByPackageIndex)
            addFromIndex(KotlinTopLevelTypeAliasByPackageIndex)
        }

        if (kindFilter.acceptsKinds(DescriptorKindFilter.FUNCTIONS_MASK)) {
            addFromIndex(KotlinTopLevelFunctionByPackageIndex)
        }

        if (kindFilter.acceptsKinds(DescriptorKindFilter.VARIABLES_MASK)) {
            addFromIndex(KotlinTopLevelPropertyByPackageIndex)
        }

        return result
    }

    private val _declarationNames: Set<Name> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val names = hashSetOf<Name>()
        runReadAction {
            FileBasedIndex.getInstance()
                .processValues(
                    KotlinPackageSourcesMemberNamesIndex.NAME,
                    fqName.asString(),
                    null,
                    FileBasedIndex.ValueProcessor { _, values ->
                        ProgressManager.checkCanceled()
                        for (value in values) {
                            names += Name.identifier(value).safeNameForLazyResolve()
                        }
                        true
                    }, searchScope
                )
        }
        names
    }

    override fun getDeclarationNames(): Set<Name> = _declarationNames

    override fun getClassOrObjectDeclarations(name: Name): Collection<KtClassOrObjectInfo<*>> {
        val childName = childName(name)
        if (isShortNameFilteringEnabled && !name.isSpecial) {
            val shortNames = ShortNamesCacheService.getInstance(project).getShortNameCandidates(name.asString())
            if (childName !in shortNames) {
                return emptyList()
            }
        }
        val ktClassOrObjectInfos = runReadAction {
            val results = arrayListOf<KtClassOrObjectInfo<*>>()
            KotlinFullClassNameIndex.processElements(childName, project, searchScope) {
                ProgressManager.checkCanceled()
                results += KtClassInfoUtil.createClassOrObjectInfo(it)
                true
            }
            results
        }
        return ktClassOrObjectInfos
    }

    @ApiStatus.Internal
    fun checkClassOrObjectDeclarations(name: Name) {
        val childName = childName(name)
        if (KotlinFullClassNameIndex.get(childName, project, searchScope).isEmpty()) {
            val processor = object : CommonProcessors.FindFirstProcessor<String>() {
                override fun accept(t: String?): Boolean = childName == t
            }
            KotlinFullClassNameIndex.processAllKeys(searchScope, null, processor)
            val everyObjects = KotlinFullClassNameIndex.get(childName, project, GlobalSearchScope.everythingScope(project))
            if (processor.isFound || everyObjects.isNotEmpty()) {
                project.messageBus.syncPublisher(KotlinCorruptedIndexListener.TOPIC).corruptionDetected()

                throw IllegalStateException(
                    """
                     | KotlinFullClassNameIndex ${if (processor.isFound) "has" else "has not"} '$childName' key.
                     | No value for it in $searchScope.
                     | Everything scope has ${everyObjects.size} objects${if (everyObjects.isNotEmpty())" locations: ${everyObjects.map { it.containingFile.virtualFile }}" else ""}.
                     | 
                     | ${if (everyObjects.isEmpty()) "Please try File -> ${if (isApplicationInternalMode()) "Cache recovery -> " else ""}Repair IDE" else ""}
                    """.trimMargin()
                )
            }
        }
    }

    override fun getScriptDeclarations(name: Name): Collection<KtScriptInfo> =
        runReadAction {
            KotlinScriptFqnIndex[childName(name), project, searchScope]
                .map(::KtScriptInfo)
        }


    override fun getFunctionDeclarations(name: Name): Collection<KtNamedFunction> =
        runReadAction {
            KotlinTopLevelFunctionFqnNameIndex[childName(name), project, searchScope]
        }

    override fun getPropertyDeclarations(name: Name): Collection<KtProperty> =
        runReadAction {
            KotlinTopLevelPropertyFqnNameIndex[childName(name), project, searchScope]
        }

    override fun getDestructuringDeclarationsEntries(name: Name): Collection<KtDestructuringDeclarationEntry> {
        return emptyList()
    }

    override fun getAllDeclaredSubPackages(nameFilter: (Name) -> Boolean): Collection<FqName> {
        return KotlinPackageIndexUtils.getSubPackageFqNames(fqName, searchScope, nameFilter)
    }

    override fun getPackageFiles(): Collection<KtFile> {
        return KotlinPackageIndexUtils.findFilesWithExactPackage(fqName, searchScope, project)
    }

    override fun containsFile(file: KtFile): Boolean {
        return searchScope.contains(file.virtualFile ?: return false)
    }

    override fun getTypeAliasDeclarations(name: Name): Collection<KtTypeAlias> {
        return KotlinTopLevelTypeAliasFqNameIndex[childName(name), project, searchScope]
    }

    private fun childName(name: Name): String {
        return fqName.child(name.safeNameForLazyResolve()).asString()
    }
}
