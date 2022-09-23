// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.asJava.classes

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.perf.UltraLightChecker
import org.jetbrains.kotlin.idea.perf.UltraLightChecker.checkByJavaFile
import org.jetbrains.kotlin.idea.perf.UltraLightChecker.checkDescriptorsLeak
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractUltraLightClassLoadingTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    open fun doTest(testDataPath: String) {
        val testDataFile = File(testDataPath)
        val sourceText = testDataFile.readText()
        InTextDirectivesUtils.checkIfMuted(sourceText)

        withCustomCompilerOptions(sourceText, project, module) {
            val file = myFixture.addFileToProject(testDataFile.name, sourceText) as KtFile

            UltraLightChecker.checkForReleaseCoroutine(sourceText, module)

            val checkByJavaFile = InTextDirectivesUtils.isDirectiveDefined(sourceText, "CHECK_BY_JAVA_FILE")

            val ktClassOrObjects = UltraLightChecker.allClasses(file)

            if (checkByJavaFile) {
                val classList = ktClassOrObjects.mapNotNull { it.toLightClass() }
                checkByJavaFile(testDataPath, classList)
                classList.forEach { checkDescriptorsLeak(it) }
            } else {
                for (ktClass in ktClassOrObjects) {
                    val ultraLightClass = UltraLightChecker.checkClassEquivalence(ktClass)
                    if (ultraLightClass != null) {
                        checkDescriptorsLeak(ultraLightClass)
                    }
                }
            }
        }
    }
}
