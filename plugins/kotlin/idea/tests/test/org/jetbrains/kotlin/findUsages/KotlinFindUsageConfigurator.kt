// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.findUsages

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

interface KotlinFindUsageConfigurator {
    val project: Project
    val file: PsiFile
    val module: Module
    val editor: Editor?
    val elementAtCaret: PsiElement
    fun configureByFile(filePath: String)
    fun configureByFiles(filePaths: List<String>)
    fun renameElement(element: PsiElement, newName: String)

    companion object {
        fun fromFixture(fixture: JavaCodeInsightTestFixture): KotlinFindUsageConfigurator = object : KotlinFindUsageConfigurator {
            override val project: Project get() = fixture.project
            override val file: PsiFile get() = fixture.file
            override val module: Module get() = fixture.module
            override val editor: Editor? get() = fixture.editor
            override val elementAtCaret: PsiElement get() = fixture.elementAtCaret
            override fun configureByFile(filePath: String) {
                fixture.configureByFile(filePath)
            }

            override fun configureByFiles(filePaths: List<String>) {
                fixture.configureByFiles(*filePaths.toTypedArray())
            }

            override fun renameElement(element: PsiElement, newName: String) {
                fixture.renameElement(element, newName)
            }
        }
    }
}
