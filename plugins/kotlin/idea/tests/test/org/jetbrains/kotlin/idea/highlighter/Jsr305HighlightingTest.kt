// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.load.java.ReportLevel
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@TestRoot("idea/tests")
@TestMetadata("testData/highlighterJsr305/project")
@RunWith(JUnit38ClassRunner::class)
class Jsr305HighlightingTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor {
        val foreignAnnotationsJar = TestKotlinArtifacts.jsr305
        check(foreignAnnotationsJar.exists()) { "${foreignAnnotationsJar.path} does not exist" }
        val libraryJar = KotlinCompilerStandalone(
            listOf(IDEA_TEST_DATA_DIR.resolve("highlighterJsr305/library")),
            classpath = listOf(foreignAnnotationsJar)
        ).compile()

        return object : KotlinJdkAndLibraryProjectDescriptor(listOf(TestKotlinArtifacts.kotlinStdlib, foreignAnnotationsJar, libraryJar)) {
            override fun configureModule(module: Module, model: ModifiableRootModel) {
                super.configureModule(module, model)
                module.createFacet(JvmPlatforms.jvm8)
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
