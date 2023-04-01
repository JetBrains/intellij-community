// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.diff.DiffContentFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.ExpectedHighlightingData
import org.jetbrains.kotlin.idea.KotlinDaemonAnalyzerTestCase
import org.jetbrains.kotlin.idea.KotlinFileType
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinDiffHighlighterTest : KotlinDaemonAnalyzerTestCase() {
    fun testDiff() {
        val file = configureByText(
            KotlinFileType.INSTANCE,
            """
                package one
                open class Foo
                class Boo: Foo()
            """.trimIndent()
        )

        val document = DiffContentFactory.getInstance().create(
            project,
            """
                open class Foo
                class Boo: Foo()
            """.trimIndent(),
            file.virtualFile
        )

        myFile = PsiDocumentManager.getInstance(project).getPsiFile(document.document)
        myEditor = createEditor(myFile.virtualFile)
        checkHighlighting(ExpectedHighlightingData(document.document, true, false))
    }

    override fun doTestLineMarkers(): Boolean = true
}
