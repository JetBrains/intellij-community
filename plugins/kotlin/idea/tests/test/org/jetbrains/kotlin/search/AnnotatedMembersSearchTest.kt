// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.search

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.testFramework.LightProjectDescriptor
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.search.PsiBasedClassResolver
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

abstract class AbstractAnnotatedMembersSearchTest : AbstractSearcherTest() {
    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE
    }

    fun doTest(unused: String) {
        val testDataFile = dataFile()

        myFixture.configureByFile(fileName())
        val fileText = FileUtil.loadFile(testDataFile, true)
        val directives = InTextDirectivesUtils.findListWithPrefixes(fileText, "// ANNOTATION: ")

        TestCase.assertFalse("Specify ANNOTATION directive in test file", directives.isEmpty())

        val annotationClassName = directives.first()
        val psiClass = getPsiClass(annotationClassName)
        PsiBasedClassResolver.trueHits.set(0)
        PsiBasedClassResolver.falseHits.set(0)

        val actualResult = AnnotatedElementsSearch.searchElements(
            psiClass,
            projectScope,
            PsiModifierListOwner::class.java
        )

        checkResult(testDataFile, actualResult)

        val optimizedTrue = InTextDirectivesUtils.getPrefixedInt(fileText, "// OPTIMIZED_TRUE:")
        if (optimizedTrue != null) {
            TestCase.assertEquals(optimizedTrue.toInt(), PsiBasedClassResolver.trueHits.get())
        }

        val optimizedFalse = InTextDirectivesUtils.getPrefixedInt(fileText, "// OPTIMIZED_FALSE:")
        if (optimizedFalse != null) {
            TestCase.assertEquals(optimizedFalse.toInt(), PsiBasedClassResolver.falseHits.get())
        }
    }
}
