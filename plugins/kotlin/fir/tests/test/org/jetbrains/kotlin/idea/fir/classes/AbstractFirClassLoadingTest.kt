// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.classes

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.classes.AbstractIdeLightClassesByPsiTest
import org.jetbrains.kotlin.asJava.classes.UltraLightChecker.allClasses
import org.jetbrains.kotlin.asJava.classes.UltraLightChecker.getJavaFileForTest
import org.jetbrains.kotlin.asJava.classes.UltraLightChecker.renderLightClasses
import org.jetbrains.kotlin.executeOnPooledThreadInReadAction
import org.jetbrains.kotlin.idea.fir.findUsages.doTestWithFIRFlagsByPath
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.util.slashedPath
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractFirClassLoadingTest : AbstractIdeLightClassesByPsiTest() {

    override fun isFirPlugin(): Boolean = true

    override fun doMultiFileTest(files: List<PsiFile>, globalDirectives: Directives) = doTestWithFIRFlagsByPath(testDataDirectory.slashedPath) {
        doTestImpl(testDataDirectory.slashedPath)
    }

    private fun doTestImpl(testDataPath: String) {

        val testDataFile = File(testDataPath)
        val sourceText = testDataFile.readText()
        withCustomCompilerOptions(sourceText, project, module) {
            val file = myFixture.addFileToProject(testDataPath, sourceText) as KtFile

            val classFabric = KotlinAsJavaSupport.getInstance(project)

            val expectedTextFile = getJavaFileForTest(testDataPath)

            val renderedClasses = executeOnPooledThreadInReadAction {
                val lightClasses = allClasses(file).mapNotNull { classFabric.getLightClass(it) }
                renderLightClasses(testDataPath, lightClasses)
            }

            KotlinTestUtils.assertEqualsToFile(expectedTextFile, renderedClasses)
        }
    }
}
