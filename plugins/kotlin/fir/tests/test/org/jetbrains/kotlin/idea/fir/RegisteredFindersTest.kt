// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir

import com.intellij.psi.NonClasspathClassFinder
import com.intellij.psi.PsiElementFinder
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class RegisteredFindersTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_LATEST

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
                            "Consider updating KotlinJavaPsiFacade",
                    isKnown
                )
            }
        }

        expectedFindersNames.removeAll(optionalFindersNames)

        Assert.assertTrue("Some finders wasn't found: $expectedFindersNames", expectedFindersNames.isEmpty())
    }
}
