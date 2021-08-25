// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.VcsCodeAuthorInlayHintsProvider
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

internal class KotlinCodeAuthorInlayHintsProvider : VcsCodeAuthorInlayHintsProvider() {

    override fun isAccepted(element: PsiElement): Boolean = element is KtClass || element is KtNamedFunction
}