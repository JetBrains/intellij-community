// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.liveTemplates.macro

import com.intellij.codeInsight.template.JavaPsiElementResult
import com.intellij.psi.PsiNamedElement

class KotlinPsiElementResult(element: PsiNamedElement) : JavaPsiElementResult(element) {
    override fun toString() = (element as PsiNamedElement).name ?: ""
}
