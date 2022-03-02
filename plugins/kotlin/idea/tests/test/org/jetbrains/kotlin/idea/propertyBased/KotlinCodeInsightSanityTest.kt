// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.propertyBased

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.propertyBased.*
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.PropertyChecker
import org.jetbrains.kotlin.idea.quickfix.AbstractImportFixInfo
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import java.io.File
import java.util.function.Function
import java.util.function.Supplier

@SkipSlowTestLocally
class KotlinCodeInsightSanityTest : KotlinLightCodeInsightFixtureTestCase() {
    private val seed: String? = System.getProperty("seed")

    override fun setUp() {
        super.setUp()
        RecursionManager.disableMissedCacheAssertions(testRootDisposable)
    }

    override fun tearDown() {
        // remove jdk if it was created during highlighting to avoid leaks
        try {
            JavaAwareProjectJdkTableImpl.removeInternalJdkInTests()
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = ProjectDescriptorWithStdlibSources.INSTANCE

    fun testRandomActivity() {
        enableInspections()
        AbstractImportFixInfo.ignoreModuleError(testRootDisposable)
        val actionSupplier = actionOnKotlinFiles { file: PsiFile ->
            Generator.sampledFrom(
                InvokeIntention(file, KotlinIntentionPolicy()),
                //TODO: support completion mutators
                //InvokeCompletion(file, KotlinCompletionPolicy()),
                StripTestDataMarkup(file),
                DeleteRange(file)
            )
        }
        PropertyChecker
            .customized()
            .run {
                seed?.let { this.rechecking(it) } ?: this
            }
            .checkScenarios(actionSupplier)
    }

    private fun enableInspections() {
        MadTestingUtil.enableAllInspections(project)
    }

    private fun actionOnKotlinFiles(fileActions: Function<PsiFile, Generator<out MadTestingAction>>): Supplier<MadTestingAction?> {
        return MadTestingUtil.actionsOnFileContents(myFixture, PathManager.getHomePath(),
                                                    { f: File -> f.name.endsWith(".kt") }, fileActions)
    }
}
