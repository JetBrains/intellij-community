// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.findUsages

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.testFramework.assertEqualsToFile
import com.intellij.usages.UsageInfoToUsageConverter
import com.intellij.usages.similarity.clustering.ClusteringSearchSession
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.File
import java.util.concurrent.Callable

abstract class AbstractKotlinGroupUsagesBySimilarityTest: AbstractFindUsagesTest() {
    override fun <T : PsiElement> doTest(path: String) {
        myFixture.configureByFile(getTestName(true) + ".kt")
        val findUsages = findUsages(myFixture.elementAtCaret, null, false, myFixture.project)
        val session = ClusteringSearchSession()
        ReadAction.nonBlocking(Callable {
            findUsages.forEach { usage ->
                UsageInfoToUsageConverter.convertToSimilarUsage(arrayOf(myFixture.elementAtCaret), usage, session)
            }
        }
        ).submit(AppExecutorUtil.getAppExecutorService()).get()

        val file = File(testDataDirectory, getTestName(true) + ".results.txt")
        assertEqualsToFile("", file, session.clusters.toString())
    }
}