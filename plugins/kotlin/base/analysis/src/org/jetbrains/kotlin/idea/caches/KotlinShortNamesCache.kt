// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.util.ArrayUtil
import com.intellij.util.Processor
import com.intellij.util.Processors
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.indexing.IdFilter
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.defaultImplsChild
import org.jetbrains.kotlin.asJava.finder.JavaElementFinder
import org.jetbrains.kotlin.asJava.getAccessorLightMethods
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinFileFacadeShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinFunctionShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinPropertyShortNameIndex
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.java.getPropertyNamesCandidatesByAccessorName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtValVarKeywordOwner
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

class KotlinShortNamesCache(private val project: Project) : PsiShortNamesCache() {
    companion object {
        private val LOG = Logger.getInstance(KotlinShortNamesCache::class.java)
    }

    //hacky way to avoid searches for Kotlin classes, when looking for Java (from Kotlin)
    val disableSearch: ThreadLocal<Boolean> = object : ThreadLocal<Boolean>() {
        override fun initialValue(): Boolean = false
    }

    //region Classes

    override fun processAllClassNames(processor: Processor<in String>): Boolean {
        if (disableSearch.get()) return true
        return KotlinClassShortNameIndex.processAllKeys(project, processor) &&
                KotlinFileFacadeShortNameIndex.processAllKeys(project, processor)
    }

    override fun processAllClassNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?): Boolean {
        if (disableSearch.get()) return true
        return processAllClassNames(processor)
    }

    /**
     * Return kotlin class names from project sources which should be visible from java.
     */
    override fun getAllClassNames(): Array<String> {
        if (disableSearch.get()) return ArrayUtil.EMPTY_STRING_ARRAY
        return withArrayProcessor(ArrayUtil.EMPTY_STRING_ARRAY) { processor ->
            processAllClassNames(processor)
        }
    }

    override fun processClassesWithName(
        name: String,
        processor: Processor<in PsiClass>,
        scope: GlobalSearchScope,
        filter: IdFilter?
    ): Boolean {
        if (disableSearch.get()) return true
        val effectiveScope = kotlinDeclarationsVisibleFromJavaScope(scope)
        val fqNameProcessor = Processor<FqName> { fqName: FqName? ->
            if (fqName == null) return@Processor true

            val isInterfaceDefaultImpl = name == JvmAbi.DEFAULT_IMPLS_CLASS_NAME && fqName.shortName().asString() != name

            if (fqName.shortName().asString() != name && !isInterfaceDefaultImpl) {
                LOG.error(
                    "A declaration obtained from index has non-matching name:" +
                            "\nin index: $name" +
                            "\ndeclared: ${fqName.shortName()}($fqName)"
                )

                return@Processor true
            }

            val fqNameToSearch = if (isInterfaceDefaultImpl) fqName.defaultImplsChild() else fqName

            val psiClass = JavaElementFinder.getInstance(project).findClass(fqNameToSearch.asString(), effectiveScope)
                ?: return@Processor true

            return@Processor processor.process(psiClass)
        }

        val allKtClassOrObjectsProcessed =
          KotlinClassShortNameIndex.processElements(
              name,
              project,
              effectiveScope,
              filter
            ) { ktClassOrObject ->
                fqNameProcessor.process(ktClassOrObject.fqName)
            }
        if (!allKtClassOrObjectsProcessed) {
            return false
        }

        return KotlinFileFacadeShortNameIndex.processElements(
          name,
          project,
          effectiveScope,
          filter
        ) { ktFile ->
            fqNameProcessor.process(ktFile.javaFileFacadeFqName)
        }
    }

    /**
     * Return class names form kotlin sources in given scope which should be visible as Java classes.
     */
    override fun getClassesByName(name: String, scope: GlobalSearchScope): Array<PsiClass> {
        if (disableSearch.get()) return PsiClass.EMPTY_ARRAY
        return withArrayProcessor(PsiClass.EMPTY_ARRAY) { processor ->
            processClassesWithName(name, processor, scope, null)
        }
    }

    private fun kotlinDeclarationsVisibleFromJavaScope(scope: GlobalSearchScope): GlobalSearchScope {
        val noBuiltInsScope: GlobalSearchScope = object : GlobalSearchScope(project) {
            override fun isSearchInModuleContent(aModule: Module) = true
            override fun compare(file1: VirtualFile, file2: VirtualFile) = 0
            override fun isSearchInLibraries() = true
            override fun contains(file: VirtualFile) = !FileTypeRegistry.getInstance().isFileOfType(file, KotlinBuiltInFileType)
        }
        return KotlinSourceFilterScope.projectSourcesAndLibraryClasses(scope, project).intersectWith(noBuiltInsScope)
    }
    //endregion

    //region Methods

    override fun processAllMethodNames(
        processor: Processor<in String>,
        scope: GlobalSearchScope,
        filter: IdFilter?
    ): Boolean {
        if (disableSearch.get()) return true
        return processAllMethodNames(processor)
    }

    override fun getAllMethodNames(): Array<String> {
        if (disableSearch.get()) ArrayUtil.EMPTY_STRING_ARRAY
        return withArrayProcessor(ArrayUtil.EMPTY_STRING_ARRAY) { processor ->
            processAllMethodNames(processor)
        }
    }

    private fun processAllMethodNames(processor: Processor<in String>): Boolean {
        if (disableSearch.get()) return true
        if (!KotlinFunctionShortNameIndex.processAllKeys(project, processor)) {
            return false
        }

        return KotlinPropertyShortNameIndex.processAllKeys(project) { name ->
            return@processAllKeys processor.process(JvmAbi.setterName(name)) && processor.process(JvmAbi.getterName(name))
        }
    }

    override fun processMethodsWithName(
        name: String,
        processor: Processor<in PsiMethod>,
        scope: GlobalSearchScope,
        filter: IdFilter?
    ): Boolean {
        if (disableSearch.get() || DumbService.isDumb(project)) return true
        val allFunctionsProcessed =
          KotlinFunctionShortNameIndex.processElements(
            name,
            project,
            scope,
            filter
          ) { ktNamedFunction ->
              ProgressManager.checkCanceled()
              val methods = LightClassUtil.getLightClassMethodsByName(ktNamedFunction, name).toList()
              methods.all(processor::process)
          }
        if (!allFunctionsProcessed) {
            return false
        }

        for (propertyName in getPropertyNamesCandidatesByAccessorName(Name.identifier(name))) {
            val allProcessed =
              KotlinPropertyShortNameIndex.processElements(propertyName.asString(), project, scope, filter) { ktNamedDeclaration ->
                  ProgressManager.checkCanceled()
                  if (ktNamedDeclaration is KtValVarKeywordOwner) {
                      if (ktNamedDeclaration.isPrivate() || KotlinPsiHeuristics.hasJvmFieldAnnotation(ktNamedDeclaration)) {
                          return@processElements true
                      }
                  }
                  ktNamedDeclaration.getAccessorLightMethods()
                    .asSequence()
                    .filter { it.name == name }
                    .all(processor::process)
              }
            if (!allProcessed) {
                return false
            }
        }

        return true
    }

    override fun getMethodsByName(name: String, scope: GlobalSearchScope): Array<PsiMethod> {
        if (disableSearch.get()) return PsiMethod.EMPTY_ARRAY
        return withArrayProcessor(PsiMethod.EMPTY_ARRAY) { processor ->
            processMethodsWithName(name, processor, scope, null)
        }
    }

    override fun getMethodsByNameIfNotMoreThan(name: String, scope: GlobalSearchScope, maxCount: Int): Array<PsiMethod> {
        if (disableSearch.get()) return PsiMethod.EMPTY_ARRAY
        require(maxCount >= 0)

        return withArrayProcessor(PsiMethod.EMPTY_ARRAY) { processor ->
            processMethodsWithName(
                name,
                { psiMethod ->
                    processor.size != maxCount && processor.process(psiMethod)
                },
                scope,
                null
            )
        }
    }

    override fun processMethodsWithName(
        name: String,
        scope: GlobalSearchScope,
        processor: Processor<in PsiMethod>
    ): Boolean {
        if (disableSearch.get()) return true
        return ContainerUtil.process(getMethodsByName(name, scope), processor)
    }
    //endregion

    //region Fields

    override fun processAllFieldNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?): Boolean {
        if (disableSearch.get()) return true
        return processAllFieldNames(processor)
    }

    override fun getAllFieldNames(): Array<String> {
        if (disableSearch.get()) return ArrayUtil.EMPTY_STRING_ARRAY
        return withArrayProcessor(ArrayUtil.EMPTY_STRING_ARRAY) { processor ->
            processAllFieldNames(processor)
        }
    }

    private fun processAllFieldNames(processor: Processor<in String>): Boolean {
        if (disableSearch.get()) return true
        return KotlinPropertyShortNameIndex.processAllKeys(project, processor)
    }

    override fun processFieldsWithName(
        name: String,
        processor: Processor<in PsiField>,
        scope: GlobalSearchScope,
        filter: IdFilter?
    ): Boolean {
        if (disableSearch.get() || DumbService.isDumb(project)) return true
        return KotlinPropertyShortNameIndex.processElements(
          name,
          project,
          scope,
          filter
        ) { ktNamedDeclaration ->
            val field = LightClassUtil.getLightClassBackingField(ktNamedDeclaration)
                        ?: return@processElements true

            return@processElements processor.process(field)
        }
    }

    override fun getFieldsByName(name: String, scope: GlobalSearchScope): Array<PsiField> {
        if (disableSearch.get()) return PsiField.EMPTY_ARRAY
        return withArrayProcessor(PsiField.EMPTY_ARRAY) { processor ->
            processFieldsWithName(name, processor, scope, null)
        }
    }

    override fun getFieldsByNameIfNotMoreThan(name: String, scope: GlobalSearchScope, maxCount: Int): Array<PsiField> {
        if (disableSearch.get()) return PsiField.EMPTY_ARRAY
        require(maxCount >= 0)

        return withArrayProcessor(PsiField.EMPTY_ARRAY) { processor ->
            processFieldsWithName(
                name,
                { psiField ->
                    processor.size != maxCount && processor.process(psiField)
                },
                scope,
                null
            )
        }
    }
    //endregion

    private inline fun <T> withArrayProcessor(
        result: Array<T>,
        process: (CancelableArrayCollectProcessor<T>) -> Unit
    ): Array<T> {
        return CancelableArrayCollectProcessor<T>().also { processor ->
            process(processor)
        }.toArray(result)
    }

    private class CancelableArrayCollectProcessor<T> : Processor<T> {
        private val set = HashSet<T>()
        private val processor = Processors.cancelableCollectProcessor(set)

        override fun process(value: T): Boolean {
            return processor.process(value)
        }

        val size: Int get() = set.size

        fun toArray(a: Array<T>): Array<T> = set.toArray(a)
    }

    override fun getLanguage(): Language {
        return KotlinLanguage.INSTANCE
    }
}
