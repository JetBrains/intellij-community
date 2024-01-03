// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import com.intellij.diff.DiffContentFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.KotlinJvmLightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class OrphanOutsiderHighlightingTest : NewLightKotlinCodeInsightFixtureTestCase() {
    override val pluginKind: KotlinPluginMode
        get() = KotlinPluginMode.K2

    fun testDiff() {
        // Orphan outsider files are analyzed as out-of-content-root
        // Error/warning highlighting is unavailable

        val documentText = """
            package test
            <symbolName>abstract</symbolName> <symbolName>sealed</symbolName> class <symbolName>Foo</symbolName>
        """.trimIndent()

        val diffContentFactory = DiffContentFactory.getInstance()
        val document = diffContentFactory.create(project, documentText, KotlinFileType.INSTANCE).document
        document.setReadOnly(false) // For 'ExpectedHighlightingData'

        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)!!
        myFixture.openFileInEditor(psiFile.virtualFile)
      val data = ExpectedHighlightingData(
        editor.getDocument(), true, false, false, false)
      data.checkSymbolNames()
      data.init()
      (myFixture as CodeInsightTestFixtureImpl).collectAndCheckHighlighting(data)
    }

    override fun getProjectDescriptor() = KotlinJvmLightProjectDescriptor.DEFAULT
    override fun getTestDataPath() = KotlinRoot.PATH.toString()
}