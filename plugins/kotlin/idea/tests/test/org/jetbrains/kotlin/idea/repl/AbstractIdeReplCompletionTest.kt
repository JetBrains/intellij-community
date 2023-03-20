// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.repl

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.console.KotlinConsoleKeeper
import org.jetbrains.kotlin.console.KotlinConsoleRunner
import org.jetbrains.kotlin.idea.completion.test.KotlinFixtureCompletionBaseTestCase
import org.jetbrains.kotlin.idea.completion.test.testCompletion
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.allowProjectRootAccess
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.disposeVfsRootAccess
import java.io.File

abstract class AbstractIdeReplCompletionTest : KotlinFixtureCompletionBaseTestCase() {
    private var vfsDisposable: Ref<Disposable>? = null
    private var consoleRunner: KotlinConsoleRunner? = null

    override fun setUp() {
        super.setUp()
        vfsDisposable = allowProjectRootAccess(this)
        consoleRunner = KotlinConsoleKeeper.getInstance(project).run(module)!!
        ScriptConfigurationManager.updateScriptDependenciesSynchronously(consoleRunner!!.consoleFile)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { disposeVfsRootAccess(vfsDisposable) },
            ThrowableRunnable {
                consoleRunner?.dispose()
                consoleRunner = null
            },
            ThrowableRunnable { super.tearDown() }
        )
    }

    override fun getPlatform() = JvmPlatforms.unspecifiedJvmPlatform
    override fun defaultCompletionType() = CompletionType.BASIC

    override fun doTest(testPath: String) {
        val runner = consoleRunner!!
        val file = File(testPath)
        val lines = file.readLines()
        lines.prefixedWith(">> ").forEach { runner.successfulLine(it) } // not actually executing anything, only simulating
        val codeSample = lines.prefixedWith("-- ").joinToString("\n")

        runWriteAction {
            val editor = runner.consoleView.editorDocument
            editor.setText(codeSample)
            FileDocumentManager.getInstance().saveDocument(runner.consoleView.editorDocument)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }

        myFixture.configureFromExistingVirtualFile(runner.consoleFile.virtualFile)
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.getLineEndOffset(0))

        testCompletion(file.readText(), getPlatform(), { completionType, count -> myFixture.complete(completionType, count) })
    }

    private fun List<String>.prefixedWith(prefix: String) = filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()
}