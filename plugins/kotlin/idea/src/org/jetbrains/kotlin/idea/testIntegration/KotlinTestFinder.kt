// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.testIntegration

import com.intellij.codeInsight.TestFrameworks
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.testIntegration.JavaTestFinder
import com.intellij.testIntegration.TestFinderHelper
import com.intellij.util.CommonProcessors
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacadeImpl
import org.jetbrains.kotlin.asJava.classes.KtLightClassForSourceDeclaration
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import java.util.regex.Pattern

// Based on com.intellij.testIntegration.JavaTestFinder.JavaTestFinder implementation
// TODO: We can reuse JavaTestFinder if Kotlin classes have their isPhysical() return true
class KotlinTestFinder : JavaTestFinder() {
    override fun findSourceElement(from: PsiElement): PsiClass? {
        super.findSourceElement(from)?.let { return it }

        from.parentsWithSelf.filterIsInstance<KtClassOrObject>().firstOrNull { !it.isLocal }?.let {
            return if (it.resolveToDescriptorIfAny() == null) null else it.toLightClass()
        }

        return (from.containingFile as? KtFile)?.findFacadeClass()
    }

    override fun isTest(element: PsiElement): Boolean {
        val sourceElement = findSourceElement(element) ?: return false
        return super.isTest(sourceElement)
    }

    override fun findClassesForTest(element: PsiElement): Collection<PsiElement> {
        val klass = findSourceElement(element) ?: return emptySet()

        val scope = getSearchScope(element, true)

        val cache = PsiShortNamesCache.getInstance(element.project)

        val frameworks = TestFrameworks.getInstance()
        val classesWithWeights = ArrayList<Pair<out PsiNamedElement, Int>>()
        for (candidateNameWithWeight in TestFinderHelper.collectPossibleClassNamesWithWeights(klass.name)) {
            for (eachClass in cache.getClassesByName(candidateNameWithWeight.first, scope)) {
                if (eachClass.isAnnotationType || frameworks.isTestClass(eachClass)) continue

                if (eachClass is KtLightClassForFacadeImpl) {
                    eachClass.files.mapTo(classesWithWeights) { Pair.create(it, candidateNameWithWeight.second) }
                } else if (eachClass.isPhysical || eachClass is KtLightClassForSourceDeclaration) {
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

        val klassName = klass.name!!
        val pattern = Pattern.compile(".*" + StringUtil.escapeToRegexp(klassName) + ".*", Pattern.CASE_INSENSITIVE)

        val scope = getSearchScope(klass, false)
        val frameworks = TestFrameworks.getInstance()

        val cache = PsiShortNamesCache.getInstance(klass.project)
        names@for (candidateName in cache.allClassNames) {
            if (pattern.matcher(candidateName).matches()) {
                for (candidateClass in cache.getClassesByName(candidateName, scope)) {
                    if (!(frameworks.isTestClass(candidateClass) || frameworks.isPotentialTestClass(candidateClass))) {
                        continue
                    }
                    if (!candidateClass.isPhysical && candidateClass !is KtLightClassForSourceDeclaration) {
                        continue
                    }

                    if (!processor.process(Pair.create(candidateClass, TestFinderHelper.calcTestNameProximity(klassName, candidateName)))) {
                        break@names
                    }
                }
            }
        }

        return TestFinderHelper.getSortedElements(classesWithProximities, true)
    }
}
