// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import java.io.File

abstract class AbstractKotlinArgumentsHintsProviderTest  : KotlinLightCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    fun doTest(unused: String) { // named according to the convention imposed by GenerateTests
        val dependencySuffixes = listOf(".dependency.kt", ".dependency.java", ".dependency1.kt", ".dependency2.kt")
        for (suffix in dependencySuffixes) {
            val dependencyPath = fileName().replace(".kt", suffix)
            if (File(testDataDirectory, dependencyPath).exists()) {
                myFixture.configureByFile(dependencyPath)
            }
        }

        val ktFile = myFixture.configureByFile(dataFile())

        myFixture.testInlays()
    }


}