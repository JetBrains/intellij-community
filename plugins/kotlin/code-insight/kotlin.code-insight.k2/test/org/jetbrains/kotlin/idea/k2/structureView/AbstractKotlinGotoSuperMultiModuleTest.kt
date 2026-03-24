// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.structureView

import com.intellij.codeInsight.navigation.GotoTargetHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.codeInsight.SuperDeclaration
import org.jetbrains.kotlin.idea.k2.codeinsight.KotlinGoToSuperDeclarationsHandler
import org.jetbrains.kotlin.idea.navigation.AbstractKotlinNavigationMultiModuleTest
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import java.io.File


abstract class AbstractKotlinGotoSuperMultiModuleTest : AbstractKotlinNavigationMultiModuleTest() {
    override fun getTestDataDirectory(): File = IDEA_TEST_DATA_DIR.resolve("navigation/gotoSuper/multiModule")

    override fun doNavigate(editor: Editor, file: PsiFile): GotoTargetHandler.GotoData {
        val declaration = KotlinGoToSuperDeclarationsHandler.findTargetDeclaration(file, editor)
        val result = if (declaration != null) KotlinGoToSuperDeclarationsHandler.findSuperDeclarations(declaration) else null
        val superDeclarations = when (result) {
            is KotlinGoToSuperDeclarationsHandler.HandlerResult.Multiple -> result.items.mapNotNull { it.declaration.element }.toTypedArray()
            is KotlinGoToSuperDeclarationsHandler.HandlerResult.Single -> arrayOf(result.item.declaration.element)
            null -> emptyArray<PsiElement>()
        }
        return GotoTargetHandler.GotoData(file.findElementAt(editor.caretModel.offset)!!, superDeclarations, emptyList())
    }
}

