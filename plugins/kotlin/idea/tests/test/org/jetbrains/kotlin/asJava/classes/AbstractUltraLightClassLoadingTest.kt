// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.asJava.classes

import com.intellij.psi.PsiClass
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.perf.UltraLightChecker
import org.jetbrains.kotlin.idea.perf.UltraLightChecker.checkByJavaFile
import org.jetbrains.kotlin.idea.perf.UltraLightChecker.checkDescriptorsLeak
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.psi.KtClassOrObject
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
            val additionalFile = File("$testDataPath.1")
            val additionalPsiFile = if (additionalFile.exists()) {
                myFixture.addFileToProject(additionalFile.name.replaceFirst(".kt.1", "1.kt"), additionalFile.readText())
            } else {
                null
            } as? KtFile

            val ktClassOrObjects = UltraLightChecker.allClasses(file)
            val classList = ktClassOrObjects.mapNotNull(KtClassOrObject::toLightClass) + listOfNotNull(
                file,
                additionalPsiFile,
            ).flatMap(KtFile::toLightElements).filterIsInstance<PsiClass>().distinct()

            checkByJavaFile(testDataPath, classList)
            classList.forEach { checkDescriptorsLeak(it) }
            for (ktClass in ktClassOrObjects) {
                val ultraLightClass = UltraLightChecker.checkClassEquivalence(ktClass)
                if (ultraLightClass != null) {
                    checkDescriptorsLeak(ultraLightClass)
                }
            }
        }
    }
}
