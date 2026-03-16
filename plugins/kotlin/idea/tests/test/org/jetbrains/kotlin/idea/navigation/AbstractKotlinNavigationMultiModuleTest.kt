// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.navigation

import com.intellij.codeInsight.navigation.GotoTargetHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.codeInsight.GotoSuperActionHandler
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import java.io.File


abstract class AbstractKotlinGotoSuperMultiModuleTest : AbstractKotlinNavigationMultiModuleTest() {
    override fun getTestDataDirectory(): File = IDEA_TEST_DATA_DIR.resolve("navigation/gotoSuper/multiModule")

    override fun doNavigate(editor: Editor, file: PsiFile): GotoTargetHandler.GotoData {
        val (superDeclarations, _) = GotoSuperActionHandler.SuperDeclarationsAndDescriptor.forDeclarationAtCaret(editor, file)
        return GotoTargetHandler.GotoData(file.findElementAt(editor.caretModel.offset)!!, superDeclarations.toTypedArray(), emptyList())
    }
}

