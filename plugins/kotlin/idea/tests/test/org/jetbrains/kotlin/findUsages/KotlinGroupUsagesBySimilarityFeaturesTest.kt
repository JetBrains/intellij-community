// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.findUsages

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.assertEqualsToFile
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.findUsages.similarity.KotlinUsageSimilarityFeaturesProvider
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@TestRoot("idea/tests")
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("testData/findUsages/similarity/features")
@RunWith(
    JUnit38ClassRunner::class
)
class KotlinGroupUsagesBySimilarityFeaturesTest : AbstractFindUsagesTest() {
    fun testMethodCallFromVariable() {
        doFeatureTest()
    }

    fun testFunctionFeatures() {
        doFeatureTest()
    }

    private fun doFeatureTest() {
        myFixture.configureByFile(getTestName(true) + ".kt")
        val elementAtCaret = myFixture.getReferenceAtCaretPosition()!!.element
        val features = KotlinUsageSimilarityFeaturesProvider().getFeatures(elementAtCaret)
        val file = File(testDataDirectory, getTestName(true) + ".features.txt")
        assertEqualsToFile("", file, features.bag.map { """${it.key} => ${it.value}""" }.joinToString(separator = ",\n"))
    }

}