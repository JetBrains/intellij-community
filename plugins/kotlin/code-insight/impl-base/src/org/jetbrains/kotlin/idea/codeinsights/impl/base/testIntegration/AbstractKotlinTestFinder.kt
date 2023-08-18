// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.testIntegration

import com.intellij.codeInsight.TestFrameworks
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.parentOfType
import com.intellij.testIntegration.TestFinder
import com.intellij.testIntegration.TestFinderHelper
import com.intellij.testIntegration.TestIntegrationUtils
import com.intellij.util.CommonProcessors
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import java.util.regex.Pattern

abstract class AbstractKotlinTestFinder : TestFinder {

    protected abstract fun isResolvable(classOrObject: KtClassOrObject): Boolean

    override fun findSourceElement(from: PsiElement): PsiElement? {
        from.parentsWithSelf.filterIsInstance<KtClassOrObject>().firstOrNull { !it.isLocal }?.let {
            return if (!isResolvable(it)) null else it
        }

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
        val klass = findSourceElement(element) ?: return emptySet()

        val klassName = klass.findClassName()

        val scope = getSearchScope(element, true)

        val cache = PsiShortNamesCache.getInstance(element.project)

        val frameworks = TestFrameworks.getInstance()
        val classesWithWeights = ArrayList<Pair<out PsiNamedElement, Int>>()
        for (candidateNameWithWeight in TestFinderHelper.collectPossibleClassNamesWithWeights(klassName)) {
            for (eachClass in cache.getClassesByName(candidateNameWithWeight.first, scope)) {
                if (eachClass.isAnnotationType || frameworks.isTestClass(eachClass)) continue

                if (eachClass is KtLightClassForFacade) {
                    eachClass.files.mapTo(classesWithWeights) { Pair.create(it, candidateNameWithWeight.second) }
                } else if (eachClass.isPhysical || eachClass is KtLightClass) {
                    classesWithWeights.add(Pair.create(eachClass, candidateNameWithWeight.second))
                }
            }
        }

        return TestFinderHelper.getSortedElements(classesWithWeights, false)
    }

    override fun findTestsForClass(element: PsiElement): Collection<PsiElement> {
        val klass = findSourceElement(element) ?: return emptySet()

        val classesWithProximities = ArrayList<Pair<out PsiNamedElement, Int>>()
        val processor = CommonProcessors.CollectProcessor(classesWithProximities)

        val klassName = klass.findClassName()
        val pattern = Pattern.compile(".*" + StringUtil.escapeToRegexp(klassName) + ".*", Pattern.CASE_INSENSITIVE)

        val scope = getSearchScope(klass, false)
        val frameworks = TestFrameworks.getInstance()


        val cache = PsiShortNamesCache.getInstance(klass.project)
        val classNamesProcessor = object : CommonProcessors.CollectProcessor<String>() {
            override fun accept(t: String?): Boolean {
                return t?.let { pattern.matcher(it).matches() } ?: false
            }
        }
        cache.processAllClassNames(classNamesProcessor)

        for (candidateName in classNamesProcessor.results) {
            for (candidateClass in cache.getClassesByName(candidateName, scope)) {
                if (!candidateClass.isPhysical && candidateClass !is KtLightClass) {
                    continue
                }
                if (!(frameworks.isTestClass(candidateClass) || frameworks.isPotentialTestClass(candidateClass))) {
                    continue
                }

                processor.process(Pair.create(candidateClass, TestFinderHelper.calcTestNameProximity(klassName, candidateName)))
            }
        }

        return TestFinderHelper.getSortedElements(classesWithProximities, true)
    }

    private fun PsiElement.findClassName(): String =
        when(this) {
            is KtClassOrObject -> name
            is PsiClass -> name
            is KtFile -> {
                PsiTreeUtil.findChildOfAnyType<KtFileAnnotationList>(this, KtFileAnnotationList::class.java)?.let { annotationList ->
                    annotationList.children
                        .firstOrNull { (it as? KtAnnotationEntry)?.shortName?.asString() == JvmFileClassUtil.JVM_NAME_SHORT }
                        ?.findDescendantOfType<KtStringTemplateEntry>()?.text?.let { return it }
                }
                name
            }
            else -> null
        } ?: throw KotlinExceptionWithAttachments("non-anonymous psiElement ${javaClass.name} is expected")
            .withPsiAttachment("element", this)
}
