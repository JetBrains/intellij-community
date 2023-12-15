// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.classes

import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.asJava.classes.LightClassTestCommon
import org.jetbrains.kotlin.asJava.classes.PsiElementChecker
import org.jetbrains.kotlin.asJava.classes.findLightClass
import org.jetbrains.kotlin.executeOnPooledThreadInReadAction
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.fir.findUsages.doTestWithFIRFlagsByPath
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractFirLightClassTest : KotlinLightCodeInsightFixtureTestCase() {

    override fun isFirPlugin(): Boolean = true

    fun doTest(path: String) = doTestWithFIRFlagsByPath(path) {
        doTestImpl()
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() },
        )
    }

    private fun doTestImpl() {
        val fileName = fileName()
        val extraFilePath = when {
            fileName.endsWith(fileExtension) -> fileName.replace(fileExtension, ".extra$fileExtension")
            else -> error("Invalid test data extension")
        }

        val testFiles = if (File(testDataDirectory, extraFilePath).isFile) listOf(fileName, extraFilePath) else listOf(fileName)

        myFixture.configureByFiles(*testFiles.toTypedArray())
        if ((myFixture.file as? KtFile)?.isScript() == true) {
            error { "FIR for scripts does not supported yet" }
            ScriptConfigurationManager.updateScriptDependenciesSynchronously(myFixture.file)
        }

        val ktFile = myFixture.file as KtFile
        val testData = dataFile()

        val actual = executeOnPooledThreadInReadAction {
            LightClassTestCommon.getActualLightClassText(
                testData,
                { fqName ->
                    findLightClass(fqName, ktFile, project)?.apply {
                        PsiElementChecker.checkPsiElementStructure(this)
                    }
                },
                { it }
            )
        }

        KotlinTestUtils.assertEqualsToFile(KotlinTestUtils.replaceExtension(testData, "java"), actual)
    }

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    open val fileExtension = ".kt"
}