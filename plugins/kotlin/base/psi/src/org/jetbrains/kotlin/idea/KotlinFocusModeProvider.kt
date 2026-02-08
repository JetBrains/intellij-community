// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea

import com.intellij.codeInsight.daemon.impl.focusMode.FocusModeProvider
import com.intellij.openapi.util.Segment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtScriptInitializer

class KotlinFocusModeProvider : FocusModeProvider {
    override fun calcFocusZones(file: PsiFile): List<Segment> {
      return SyntaxTraverser.psiTraverser(file)
        .postOrderDfsTraversal()
        .filter {
            (it is KtClassOrObject || it is KtFunction || it is KtClassInitializer || it is KtScriptInitializer) &&
                    with(it.parent) {
                        this is KtFile || this is KtClassBody || this.parent is KtScript
                    }
        }
        .map(PsiElement::getTextRange).toList()
    }
}