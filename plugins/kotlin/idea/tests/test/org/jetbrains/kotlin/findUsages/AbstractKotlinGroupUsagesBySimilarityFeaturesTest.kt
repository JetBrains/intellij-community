// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.findUsages

import com.intellij.psi.PsiElement
import com.intellij.testFramework.assertEqualsToFile
import org.jetbrains.kotlin.idea.findUsages.similarity.KotlinUsageSimilarityFeaturesProvider
import java.io.File

abstract class AbstractKotlinGroupUsagesBySimilarityFeaturesTest : AbstractFindUsagesTest() {
    override fun <T : PsiElement> doTest(path: String) {
        myFixture.configureByFile(getTestName(true) + ".kt")
        val elementAtCaret = myFixture.getReferenceAtCaretPosition()!!.element
        val features = KotlinUsageSimilarityFeaturesProvider().getFeatures(elementAtCaret)
        val file = File(testDataDirectory, getTestName(true) + ".features.txt")
        assertEqualsToFile("", file, features.bag.map { """${it.key} => ${it.value}""" }.joinToString(separator = ",\n"))
    }
}
