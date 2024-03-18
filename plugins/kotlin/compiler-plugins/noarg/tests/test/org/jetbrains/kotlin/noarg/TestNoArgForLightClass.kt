// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.noarg

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinProjectDescriptorWithFacet
import org.jetbrains.kotlin.idea.compilerPlugin.noarg.NO_ARG_ANNOTATION_OPTION_PREFIX
import org.jetbrains.kotlin.idea.serialization.updateCompilerArguments
import org.jetbrains.kotlin.psi.KtFile
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

private const val targetClassName = "TargetClassName"
private const val baseClassName = "BaseClassName"
private const val noArgAnnotationName = "HelloNoArg"

@RunWith(JUnit38ClassRunner::class)
class TestNoArgForLightClass : KotlinLightCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinProjectDescriptorWithFacet(KotlinPluginLayout.standaloneCompilerVersion.languageVersion, multiPlatform = false)

    override fun setUp() {
        super.setUp()

        val facet = KotlinFacet.get(module) ?: error { "Facet not found" }
        val facetSettings = facet.configuration.settings

        facetSettings.compilerArguments ?: error { "CompilerArguments not found" }

        facetSettings.updateCompilerArguments {
            pluginClasspaths = arrayOf("SomeClasspath")
            pluginOptions = arrayOf("$NO_ARG_ANNOTATION_OPTION_PREFIX$noArgAnnotationName")
        }
    }

    fun testNoArgAnnotation() {
        val file = myFixture.configureByText(
            "A.kt",
            "annotation class $noArgAnnotationName\n"
                    + "@$noArgAnnotationName class $targetClassName(val e: Int)"
        ) as KtFile

        val classes = file.classes
        assertEquals(2, classes.size)

        val targetClass = classes.firstOrNull { it.name == targetClassName }
            ?: error { "Expected class $targetClassName not found" }

        val constructors = targetClass.constructors
        assertEquals(constructors.size, 2)
        assertTrue(constructors.any { it.parameters.isEmpty() })
    }

    fun testNoArgDerivedAnnotation() {
        val file = myFixture.configureByText(
            "A.kt",
            "annotation class $noArgAnnotationName\n"
                    + "@$noArgAnnotationName class $baseClassName(val e: Int)\n"
                    + "class $targetClassName(val k: Int) : $baseClassName(k)"
        ) as KtFile

        val classes = file.classes
        assertEquals(3, classes.size)

        val targetClass = classes.firstOrNull { it.name == targetClassName }
            ?: error { "Expected class $targetClassName not found" }

        val constructors = targetClass.constructors
        assertEquals(constructors.size, 2)
        assertTrue(constructors.any { it.parameters.isEmpty() })
    }
}