// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea

import com.intellij.psi.NonClasspathClassFinder
import com.intellij.psi.PsiElementFinder
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.resolve.jvm.KotlinJavaPsiFacade
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class RegisteredFindersTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = LightCodeInsightFixtureTestCase.JAVA_LATEST

    fun testKnownNonClasspathFinder() {
        val expectedFindersNames = setOf(
            "GantClassFinder"
        ).toMutableSet()

        val optionalFindersNames = setOf(
            "GradleClassFinder",
            "AlternativeJreClassFinder",
            "IdeaOpenApiClassFinder",
            "BundledGroovyClassFinder"
        )

        PsiElementFinder.EP.getExtensions(project).forEach { finder ->
            if (finder is NonClasspathClassFinder) {
                val name = finder::class.java.simpleName
                val isKnown = expectedFindersNames.remove(name) || optionalFindersNames.contains(name)
                Assert.assertTrue(
                    "Unknown finder found: $finder, class name: $name, search in $expectedFindersNames.\n" +
                            "Consider updating ${KotlinJavaPsiFacade::class.java}",
                    isKnown
                )
            }
        }

        expectedFindersNames.removeAll(optionalFindersNames)

        Assert.assertTrue("Some finders wasn't found: $expectedFindersNames", expectedFindersNames.isEmpty())
    }
}
