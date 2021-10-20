// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.asJava.classes

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.perf.UltraLightChecker
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import java.io.File

abstract class AbstractUltraLightClassSanityTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE_WITH_STDLIB_JDK8

    fun doTest(testDataPath: String) {
        val ioFile = File(testDataPath)
        val sourceText = ioFile.readText()

        if (InTextDirectivesUtils.isDirectiveDefined(sourceText, "SKIP_SANITY_TEST")) {
            return
        }

        val file = myFixture.addFileToProject(ioFile.name, sourceText) as KtFile

        UltraLightChecker.checkForReleaseCoroutine(sourceText, module)

        if (file.safeIsScript()) {
            ScriptConfigurationManager.updateScriptDependenciesSynchronously(file)
        }

        UltraLightChecker.checkClassEquivalence(file)
    }
}