// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.asJava.classes

import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.classes.AbstractUltraLightClassLoadingTest
import org.jetbrains.kotlin.executeOnPooledThreadInReadAction
import org.jetbrains.kotlin.idea.fir.findUsages.doTestWithFIRFlagsByPath
import org.jetbrains.kotlin.idea.perf.UltraLightChecker
import org.jetbrains.kotlin.idea.perf.UltraLightChecker.getJavaFileForTest
import org.jetbrains.kotlin.idea.perf.UltraLightChecker.renderLightClasses
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.File

abstract class AbstractFirClassLoadingTest : AbstractUltraLightClassLoadingTest() {

    override fun isFirPlugin(): Boolean = true

    override fun doTest(testDataPath: String) = doTestWithFIRFlagsByPath(testDataPath) {
        doTestImpl(testDataPath)
    }

    private fun doTestImpl(testDataPath: String) {

        val testDataFile = File(testDataPath)
        val sourceText = testDataFile.readText()
        val file = myFixture.addFileToProject(testDataPath, sourceText) as KtFile

        val classFabric = KotlinAsJavaSupport.getInstance(project)

        val expectedTextFile = getJavaFileForTest(testDataPath)

        val renderedClasses = executeOnPooledThreadInReadAction {
            val lightClasses = UltraLightChecker.allClasses(file).mapNotNull { classFabric.getLightClass(it) }
            renderLightClasses(testDataPath, lightClasses)
        }

        KotlinTestUtils.assertEqualsToFile(expectedTextFile, renderedClasses)
    }
}
