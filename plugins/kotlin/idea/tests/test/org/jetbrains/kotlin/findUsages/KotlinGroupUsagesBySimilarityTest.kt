// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.findUsages

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.assertEqualsToFile
import com.intellij.usages.UsageInfoToUsageConverter
import com.intellij.usages.similarity.clustering.ClusteringSearchSession
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.findUsages.similarity.KotlinUsageSimilarityFeaturesProvider
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@TestRoot("idea/tests")
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("testData/findUsages/similarity")
@RunWith(
    JUnit38ClassRunner::class
)
class KotlinGroupUsagesBySimilarityTest : AbstractFindUsagesTest() {
    fun testSimpleFeatures() {
        myFixture.configureByFile(getTestName(true) + ".kt")
        val elementAtCaret = myFixture.getReferenceAtCaretPosition()!!.element
        val features = KotlinUsageSimilarityFeaturesProvider().getFeatures(elementAtCaret)
        assertEquals(1, features["CONTEXT: REFERENCE: "])
        assertEquals(1, features["USAGE: {CALL: inc}"])
    }

    fun testNoVariableNameFeatures() {
        doTest()
    }

    @TestMetadata("declaration.kt")
    @Throws(Exception::class)
    fun testDeclaration() {
        doTest()
    }

    @TestMetadata("general.kt")
    @Throws(Exception::class)
    fun testGeneral() {
        doTest()
    }

    @TestMetadata("chainCalls")
    fun testChainCalls() {
        doTest()
    }

    fun testListAdd() {
        doTest()
    }

    fun testFilter() {
        doTest()
    }

    fun testImport() {
        doTest()
    }

    fun testTypeReference() {
        doTest()
    }

    fun testFeaturesForClassReferences() {
        doTest()
    }

    fun testFunctionSignature() {
        doTest()
    }

    fun testFunctionFeatures() {
        doFeatureTest()
    }

    private fun doTest() {
        myFixture.configureByFile(getTestName(true) + ".kt")
        val findUsages = findUsages(myFixture.elementAtCaret, null, false, myFixture.project)
        val session = ClusteringSearchSession()
        findUsages.forEach { usage -> UsageInfoToUsageConverter.convertToSimilarUsage(arrayOf(myFixture.elementAtCaret), usage, session) }
        val file = File(testDataDirectory, getTestName(true) + ".results.txt")
        assertEqualsToFile("", file, session.clusters.toString())
    }

    private fun doFeatureTest() {
        myFixture.configureByFile(getTestName(true) + ".kt")
        val elementAtCaret = myFixture.getReferenceAtCaretPosition()!!.element
        val features = KotlinUsageSimilarityFeaturesProvider().getFeatures(elementAtCaret)
        val file = File(testDataDirectory, getTestName(true) + ".features.txt")
        assertEqualsToFile("", file, features.bag.map { """${it.key} => ${it.value}""" }.joinToString(separator = ",\n"))
    }

}