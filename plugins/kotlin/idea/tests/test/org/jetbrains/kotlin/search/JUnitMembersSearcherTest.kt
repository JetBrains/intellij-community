// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.search

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.search.searches.AnnotatedMembersSearch
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinJdkAndLibraryProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.utils.PathUtil.getResourcePathForClass
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@TestRoot("idea/tests")
@TestMetadata("testData/search/junit")
@RunWith(JUnit38ClassRunner::class)
class JUnitMembersSearcherTest : AbstractSearcherTest() {
    override fun getProjectDescriptor() = KotlinJdkAndLibraryProjectDescriptor(getResourcePathForClass(Assert::class.java))

    override val testDataDirectory: File
        get() = super.testDataDirectory

    fun testJunit3() {
        doJUnit3test()
    }

    fun testJunit4() {
        doJUnit4test()
    }

    fun testJunit4Alias() {
        doJUnit4test()
    }

    fun testJunit4FancyAlias() {
        doJUnit4test()
    }

    private fun doJUnit3test() {
        checkClassWithDirectives(IDEA_TEST_DATA_DIR.resolve("search/junit/testJunit3.kt").path)
    }

    @Throws(IOException::class)
    private fun doJUnit4test() {
        val testDataFile = dataFile()
        myFixture.configureByFile(testDataFile)
        val directives = InTextDirectivesUtils.findListWithPrefixes(FileUtil.loadFile(testDataFile, true), "// ANNOTATION: ")
        assertFalse("Specify ANNOTATION directive in test file", directives.isEmpty())
        val annotationClassName = directives[0]
        val psiClass = getPsiClass(annotationClassName)
        checkResult(testDataFile, AnnotatedMembersSearch.search(psiClass, projectScope))
    }
}