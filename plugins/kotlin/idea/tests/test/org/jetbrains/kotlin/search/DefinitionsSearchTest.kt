// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.search

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@TestRoot("idea/tests")
@TestMetadata("testData/search/definitions")
@RunWith(JUnit38ClassRunner::class)
class DefinitionsSearchTest : AbstractSearcherTest() {
    fun testNestedClass() {
        doTest()
    }

    private fun doTest() {
        val testDataFile = dataFile()

        myFixture.configureByFile(testDataFile)
        val directives = InTextDirectivesUtils.findListWithPrefixes(FileUtil.loadFile(testDataFile, true), "// CLASS: ")
        assertFalse("Specify CLASS directive in test file", directives.isEmpty())
        val superClassName = directives[0]
        val psiClass = getPsiClass(superClassName)
        checkResult(testDataFile, DefinitionsScopedSearch.search(psiClass))

        val origin = (psiClass as? KtLightClass)?.kotlinOrigin!!
        checkResult(testDataFile, DefinitionsScopedSearch.search(origin))
    }
}