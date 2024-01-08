// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.intentions.tests

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiFile
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.intentions.AbstractIntentionTestBase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import java.io.File

abstract class AbstractK2IntentionTest : AbstractIntentionTestBase() {
    override fun intentionFileName() = ".k2Intention"

    override fun afterFileNameSuffix(ktFilePath: File): String {
        return if (ktFilePath.resolveSibling(ktFilePath.name + AFTER_K2_EXTENSION).exists()) AFTER_K2_EXTENSION
        else super.afterFileNameSuffix(ktFilePath)
    }

    override fun fileName(): String {
        val fileName = super.fileName()
        val firFileName = IgnoreTests.deriveFirFileName(fileName)

        return if (File(testDataDirectory, firFileName).exists()) firFileName else fileName
    }

    override fun isFirPlugin() = true

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun doTest(unused: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFile().toPath(), IgnoreTests.DIRECTIVES.IGNORE_K2) {
            super.doTest(unused)
        }
    }

    override fun doTestFor(mainFile: File, pathToFiles: Map<String, PsiFile>, intentionAction: IntentionAction, fileText: String) {
        super.doTestFor(mainFile, pathToFiles, intentionAction, fileText)
    }

    override fun checkForErrorsAfter(fileText: String) {}
    override fun checkForErrorsBefore(fileText: String) {}

    override fun tearDown() {
        runAll(
          ThrowableRunnable { project.invalidateCaches() },
          ThrowableRunnable { super.tearDown() }
        )
    }

    companion object {
        private const val AFTER_K2_EXTENSION = ".after.k2"
    }
}