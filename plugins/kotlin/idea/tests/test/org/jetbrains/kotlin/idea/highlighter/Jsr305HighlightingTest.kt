// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.idea.artifacts.AdditionalKotlinArtifacts
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.stubs.createFacet
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinJdkAndLibraryProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.load.java.ReportLevel
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.idea.test.TestRoot

@TestRoot("idea/tests")
@TestMetadata("testData/highlighterJsr305/project")
@RunWith(JUnit38ClassRunner::class)
class Jsr305HighlightingTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor {
        val foreignAnnotationsJar = AdditionalKotlinArtifacts.jsr305
        check(foreignAnnotationsJar.exists()) { "${foreignAnnotationsJar.path} does not exist" }
        val libraryJar = KotlinCompilerStandalone(
            listOf(IDEA_TEST_DATA_DIR.resolve("highlighterJsr305/library")),
            classpath = listOf(foreignAnnotationsJar)
        ).compile()

        return object : KotlinJdkAndLibraryProjectDescriptor(
            listOf(
                KotlinArtifacts.instance.kotlinStdlib,
                foreignAnnotationsJar,
                libraryJar
            )
        ) {
            override fun configureModule(module: Module, model: ModifiableRootModel) {
                super.configureModule(module, model)
                module.createFacet(JvmPlatforms.jvm18)
                val facetSettings = KotlinFacetSettingsProvider.getInstance(module.project)?.getInitializedSettings(module)

                facetSettings?.apply {
                    val jsrStateByTestName =
                        ReportLevel.findByDescription(getTestName(true)) ?: return@apply

                    compilerSettings!!.additionalArguments += " -Xjsr305=${jsrStateByTestName.description}"
                    updateMergedArguments()
                }
            }
        }
    }

    fun testIgnore() {
        doTest()
    }

    fun testWarn() {
        doTest()
    }

    fun testStrict() {
        doTest()
    }

    fun testDefault() {
        doTest()
    }

    private fun doTest() {
        myFixture.configureByFile("${getTestName(false)}.kt")
        myFixture.checkHighlighting()
    }
}
