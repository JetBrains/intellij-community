// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.asJava.classes

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.asJava.LightClassGenerationSupport
import org.jetbrains.kotlin.idea.perf.UltraLightChecker
import org.jetbrains.kotlin.idea.perf.UltraLightChecker.checkByJavaFile
import org.jetbrains.kotlin.idea.perf.UltraLightChecker.checkDescriptorsLeak
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import java.io.File

abstract class AbstractUltraLightClassLoadingTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE

    open fun doTest(testDataPath: String) {
        val testDataFile = File(testDataPath)
        val sourceText = testDataFile.readText()
        InTextDirectivesUtils.checkIfMuted(sourceText);

        withCustomCompilerOptions(sourceText, project, module) {
            val file = myFixture.addFileToProject(testDataFile.name, sourceText) as KtFile

            UltraLightChecker.checkForReleaseCoroutine(sourceText, module)

            val checkByJavaFile = InTextDirectivesUtils.isDirectiveDefined(sourceText, "CHECK_BY_JAVA_FILE")

            val ktClassOrObjects = UltraLightChecker.allClasses(file)

            if (checkByJavaFile) {
                val classFabric = LightClassGenerationSupport.getInstance(project)
                val classList = ktClassOrObjects.mapNotNull { classFabric.createUltraLightClass(it) }
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
