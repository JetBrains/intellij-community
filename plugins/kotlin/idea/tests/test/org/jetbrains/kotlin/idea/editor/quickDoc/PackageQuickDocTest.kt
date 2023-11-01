// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.editor.quickDoc

import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator
import com.intellij.psi.PsiPackage
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.junit.Assert

class PackageQuickDocTest: KotlinLightCodeInsightFixtureTestCase() {
    fun testNoDuplicates() {
        val psiFile = myFixture.addFileToProject(
            "com/example/nodupes/Fantastic.kt", """
            package com.example.nod<caret>upes
            class Fantastic
        """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)

        val psiPackage = myFixture.elementAtCaret as PsiPackage

        val stringBuilder = StringBuilder()
        JavaDocInfoGenerator(myFixture.project, psiPackage).generateDocInfoCore(stringBuilder, false)
        val html = stringBuilder.toString()

        val hrefPattern = Regex(Regex.escape("psi_element://com.example.nodupes.Fantastic"))
        val numMatches = hrefPattern.findAll(html).count()
        Assert.assertEquals(1, numMatches)
    }
}