// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

class KotlinRefManipulator : AbstractElementManipulator<KtNameReferenceExpression>() {
    override fun handleContentChange(element: KtNameReferenceExpression, range: TextRange, newContent: String): KtNameReferenceExpression? {
        return null
    }
}