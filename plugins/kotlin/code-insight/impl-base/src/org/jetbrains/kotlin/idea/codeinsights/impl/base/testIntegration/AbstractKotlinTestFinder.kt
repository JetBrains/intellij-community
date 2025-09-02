// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.testIntegration

import com.intellij.codeInsight.TestFrameworks
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.parentOfType
import com.intellij.testIntegration.TestFinder
import com.intellij.testIntegration.TestFinderHelper
import com.intellij.testIntegration.TestFramework
import com.intellij.testIntegration.TestIntegrationUtils
import com.intellij.util.CommonProcessors
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinFileFacadeShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelPropertyFqnNameIndex
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import java.util.regex.Pattern

abstract class AbstractKotlinTestFinder : TestFinder {

    protected abstract fun isResolvable(classOrObject: KtClassOrObject): Boolean

    override fun findSourceElement(from: PsiElement): PsiElement? {
        from.parentsWithSelf.filterIsInstance<KtClassOrObject>().firstOrNull { !it.isLocal }?.let {
            return if (!isResolvable(it)) null else it
        }

        from.parentsWithSelf.firstNotNullOfOrNull {
            if (it is KtNamedFunction && it.isTopLevel || it is KtProperty && it.isTopLevel) {
                return it
            } else {
                null
            }
        }

        return findSourceElementInFile(from)
    }

    private fun findSourceElementInFile(from: PsiElement): PsiElement? {
        (from.containingFile as? KtFile)?.let { return it }

        return TestIntegrationUtils.findOuterClass(from)
    }

    override fun isTest(element: PsiElement): Boolean {
        return element.parentOfType<KtClassOrObject>(true)?.let { KotlinPsiBasedTestFramework.findTestFramework(it) } != null
    }

    private fun getModule(element: PsiElement): Module? {
        val index = ProjectRootManager.getInstance(element.project).fileIndex
        return PsiUtilCore.getVirtualFile(element)?.let(index::getModuleForFile)
    }

    private fun getSearchScope(element: PsiElement, dependencies: Boolean): GlobalSearchScope {
        return getModule(element)?.let {
            if (dependencies) GlobalSearchScope.moduleWithDependenciesScope(it) else GlobalSearchScope.moduleWithDependentsScope(it)
        } ?: GlobalSearchScope.projectScope(element.project)
    }

    override fun findClassesForTest(element: PsiElement): Collection<PsiElement> {
        val sourceElement =
            findSourceElement(element)?.takeUnless { it is KtNamedFunction }
                ?: findSourceElementInFile(element)
                ?: return emptySet()

        val containingFile = element.containingFile
        val sourcePackageName = (containingFile as? KtFile)?.packageFqName?.asString()

        val potentialName = findPotentialNames(sourceElement).firstOrNull() ?: return emptySet()

        val scope = getSearchScope(element, true)

        val project = element.project
        val cache = PsiShortNamesCache.getInstance(project).withoutLanguages(KotlinLanguage.INSTANCE)

        val frameworks = TestFrameworks.getInstance()
        val classesWithWeights = ArrayList<Pair<out PsiNamedElement, Int>>()
        val psiManager = PsiManager.getInstance(project)
        val collectPossibleNamesWithWeights = TestFinderHelper.collectPossibleClassNamesWithWeights(potentialName)

        fun addElementWithWeight(element: PsiNamedElement, proximity: Int) {
            val candidatePackageName = (element.containingFile as? KtFile)?.packageFqName?.asString()
            val packageProximity =
                if (candidatePackageName != null && sourcePackageName != null) {
                    TestFinderHelper.calcTestNameProximity(sourcePackageName, candidatePackageName)
                } else {
                    0
                }
            classesWithWeights.add(Pair.create(element, packageProximity + proximity))
        }

        fun addKotlinTopLevelFunction(n: String, proximity: Int) {
            val key = (sourcePackageName.takeUnless { it.isNullOrEmpty() }?.let { "$it." } ?: "") + n
            KotlinTopLevelFunctionFqnNameIndex.processElements(key, project, scope) {
                addElementWithWeight(it, proximity)
                true
            }
        }

        fun addKotlinTopLevelProperties(n: String, proximity: Int) {
            val key = (sourcePackageName.takeUnless { it.isNullOrEmpty() }?.let { "$it." } ?: "") + n
            KotlinTopLevelPropertyFqnNameIndex.processElements(key, project, scope) {
                addElementWithWeight(it, proximity)
                true
            }
        }

        for (candidateNameWithWeight in collectPossibleNamesWithWeights) {
            val name = candidateNameWithWeight.first
            val weight = candidateNameWithWeight.second
            for (eachClass in cache.getClassesByName(name, scope)) {
                if (eachClass.isAnnotationType || frameworks.isTestClass(eachClass)) continue

                if (eachClass is KtLightClassForFacade) {
                    eachClass.files.mapTo(classesWithWeights) { Pair.create(it, weight) }
                } else if (eachClass.isPhysical || eachClass is KtLightClass) {
                    classesWithWeights.add(Pair.create(eachClass, weight))
                }
            }

            KotlinClassShortNameIndex.processElements(name, project, scope, null) { ktClassOrObject: KtClassOrObject ->
                if (!ktClassOrObject.isAnnotation()) {
                    val notATest =
                        DumbService.getDumbAwareExtensions(project, TestFramework.EXTENSION_NAME).none { framework ->
                            framework.isTestClass(ktClassOrObject)
                        }
                    if (notATest) {
                        addElementWithWeight(ktClassOrObject, weight)
                    }
                }
                true
            }

            KotlinFileFacadeShortNameIndex.processElements(name, project, scope, null) { ktFile ->
                addElementWithWeight(ktFile, weight)
                true
            }

            addKotlinTopLevelFunction(name, weight)
            if (name.first().isUpperCase()) {
                addKotlinTopLevelFunction(name.replaceFirstChar {  it.lowercase() }, weight + 10)
            }

            addKotlinTopLevelProperties(name, weight)
            if (name.first().isUpperCase()) {
                addKotlinTopLevelProperties(name.replaceFirstChar {  it.lowercase() }, weight + 10)
            }

            FilenameIndex.processFilesByName(name + KotlinFileType.DOT_DEFAULT_EXTENSION, false, scope) { virtualFile ->
                if (virtualFile == containingFile.virtualFile) return@processFilesByName true
                val ktFile = psiManager.findFile(virtualFile) as? KtFile
                ktFile?.let {
                    addElementWithWeight(it, weight + 10)
                }
                true
            }
        }

        return TestFinderHelper.getSortedElements(classesWithWeights, true)
    }

    override fun findTestsForClass(element: PsiElement): Collection<PsiElement> {
        val sourceElement = findSourceElement(element) ?: return emptySet()
        val sourcePackageName = (sourceElement.containingFile as? KtFile)?.packageFqName?.asString()

        val classesWithProximities = ArrayList<Pair<out PsiNamedElement, Int>>()
        val processor = CommonProcessors.CollectProcessor(classesWithProximities)

        val potentialNames = findPotentialNames(sourceElement)
        val sourceName = potentialNames.firstOrNull() ?: return emptySet()
        val scope = getSearchScope(sourceElement, false)
        val frameworks = TestFrameworks.getInstance()
        val project = sourceElement.project

        val patterns = potentialNames
            .map { Pattern.compile(".*" + StringUtil.escapeToRegexp(it) + ".*", Pattern.CASE_INSENSITIVE) }

        val classNamesProcessor = object : CommonProcessors.CollectProcessor<String>() {
            override fun accept(t: String?): Boolean {
                return t?.let { patterns.any { it.matcher(t).matches() } } == true
            }
        }

        var cache = PsiShortNamesCache.getInstance(project)
        cache.processAllClassNames(classNamesProcessor, scope, null)
        cache = cache.withoutLanguages(KotlinLanguage.INSTANCE)

        val results = classNamesProcessor.results
        for (candidateName in results) {
            val classesByName = cache.getClassesByName(candidateName, scope)
            for (candidateClass in classesByName) {
                if (!candidateClass.isPhysical && candidateClass !is KtLightClass) {
                    continue
                }
                if (!(frameworks.isTestClass(candidateClass) || frameworks.isPotentialTestClass(candidateClass))) {
                    continue
                }

                processor.process(Pair.create(candidateClass,
                                              TestFinderHelper.calcTestNameProximity(sourceName, candidateName)))
            }

            KotlinClassShortNameIndex.processElements(candidateName, project, scope, null) { ktClassOrObject: KtClassOrObject ->
                val isPotentialTest =
                    DumbService.getDumbAwareExtensions(project, TestFramework.EXTENSION_NAME).any { framework ->
                        framework.isTestClass(ktClassOrObject) || framework.isPotentialTestClass(ktClassOrObject)
                    }

                if (isPotentialTest) {
                    val candidatePackageName = ktClassOrObject.containingKtFile.packageFqName.asString()
                    val proximity =
                        (sourcePackageName?.let { TestFinderHelper.calcTestNameProximity(it, candidatePackageName) } ?: 0) +
                        TestFinderHelper.calcTestNameProximity(sourceName, candidateName)
                    processor.process(
                        Pair.create<KtClassOrObject, Int>(ktClassOrObject, proximity)
                    )
                }
                true
            }
        }
        return TestFinderHelper.getSortedElements(classesWithProximities, true)
    }

    private fun findPotentialNames(element: PsiElement): Collection<String> =
        when(element) {
            is KtNamedFunction, is KtProperty -> buildSet {
                element.name?.let(::add)
                findSourceElementInFile(element)?.let {
                    this += findPotentialNames( it )
                }
            }
            is KtClassOrObject, is PsiClass -> {
                listOfNotNull(element.name)
            }
            is KtFile -> {
                setOf(element.javaFileFacadeFqName.shortName().asString(), element.name.substringBefore('.'))
            }
            is PsiNamedElement -> {
                listOfNotNull(element.name)
            }
            else -> {
                null
            }
        } ?: throw KotlinExceptionWithAttachments("non-anonymous psiElement ${javaClass.name} is expected")
            .withPsiAttachment("element", element)
}
