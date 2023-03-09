// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testIntegration

import com.intellij.execution.testframework.JvmTestDiffProvider
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtStringTemplateEntry

class KotlinTestDiffProvider : JvmTestDiffProvider() {
    override fun getStringLiteral(expected: PsiElement): PsiElement? {
        return if (expected is KtStringTemplateEntry) expected.parent else null
    }
}