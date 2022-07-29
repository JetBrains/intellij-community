// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.asJava.classes

import com.intellij.testFramework.LightProjectDescriptor
import junit.framework.TestCase
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.LightClassGenerationSupport
import org.jetbrains.kotlin.idea.asJava.renderClass
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.perf.UltraLightChecker
import org.jetbrains.kotlin.idea.perf.UltraLightChecker.checkDescriptorsLeak
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractUltraLightScriptLoadingTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE

    fun doTest(testDataPath: String) {
        val testDataFile = File(testDataPath)
        val sourceText = testDataFile.readText()
        val file = myFixture.addFileToProject(testDataFile.name, sourceText) as KtFile

        UltraLightChecker.checkForReleaseCoroutine(sourceText, module)

        TestCase.assertNotNull(file.script)
        val script = file.script!!

        ScriptConfigurationManager.updateScriptDependenciesSynchronously(file)

        val expectedTextFile = File(testDataPath.replaceFirst("\\.kts\$".toRegex(), ".java"))
        if (expectedTextFile.exists()) {
            val ultraLightScript = LightClassGenerationSupport.getInstance(script.project).createUltraLightClassForScript(script)
            val renderedResult = ultraLightScript.renderClass()
            KotlinTestUtils.assertEqualsToFile(expectedTextFile, renderedResult)
            checkDescriptorsLeak(ultraLightScript)
            return
        }

        val ultraLightClass = KotlinAsJavaSupport.getInstance(script.project).getLightClassForScript(script).also {
            assertTrue(it is KtUltraLightClassForScript)
        }!!

        checkDescriptorsLeak(ultraLightClass)
    }
}
