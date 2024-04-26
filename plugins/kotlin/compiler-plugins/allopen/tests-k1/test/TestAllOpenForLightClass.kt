// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.allopen.test

import com.intellij.lang.jvm.JvmModifier
import com.intellij.testFramework.LightProjectDescriptor
import junit.framework.TestCase
import org.jetbrains.kotlin.allopen.AbstractAllOpenDeclarationAttributeAltererExtension
import org.jetbrains.kotlin.idea.compilerPlugin.allopen.ALL_OPEN_ANNOTATION_OPTION_PREFIX
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.serialization.updateCompilerArguments
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinProjectDescriptorWithFacet
import org.jetbrains.kotlin.psi.KtFile
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class TestAllOpenForLightClass : KotlinLightCodeInsightFixtureTestCase() {

    companion object {
        val allOpenAnnotationName = AbstractAllOpenDeclarationAttributeAltererExtension.ANNOTATIONS_FOR_TESTS.first()
        const val targetClassName = "TargetClassName"
    }

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinProjectDescriptorWithFacet(KotlinPluginLayout.standaloneCompilerVersion.languageVersion, multiPlatform = false)

    override fun setUp() {
        super.setUp()

        val facet = KotlinFacet.get(module) ?: error { "Facet not found" }
        val facetSettings = facet.configuration.settings

        TestCase.assertNotNull("CompilerArguments not found", facetSettings.compilerArguments)

        facetSettings.updateCompilerArguments {
            pluginClasspaths = arrayOf("SomeClasspath")
            pluginOptions = arrayOf("$ALL_OPEN_ANNOTATION_OPTION_PREFIX$allOpenAnnotationName")
        }
    }

    fun testAllOpenAnnotation() {
        val file = myFixture.configureByText(
            "A.kt",
            "annotation class $allOpenAnnotationName\n"
                    + "@$allOpenAnnotationName class $targetClassName(val e: Int)\n {"
                    + "  fun a() {}\n"
                    + "  val b = 32\n"
                    + "}"


        ) as KtFile

        val classes = file.classes
        assertEquals(2, classes.size)

        val targetClass = classes.firstOrNull { it.name == targetClassName }
            ?: error { "Expected class $targetClassName not found" }

        assertFalse(targetClass.hasModifier(JvmModifier.FINAL))

        targetClass.methods
            .filter { !it.isConstructor }
            .forEach {
                assertFalse(it.hasModifier(JvmModifier.FINAL))
            }
    }
}