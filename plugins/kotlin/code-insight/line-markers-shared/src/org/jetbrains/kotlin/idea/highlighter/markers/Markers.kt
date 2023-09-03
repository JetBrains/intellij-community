// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter.markers

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtDeclaration

@Deprecated("use org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared.markerDeclaration", level = DeprecationLevel.ERROR)
val PsiElement.markerDeclaration: KtDeclaration?
    @ApiStatus.Internal
    get() = (this as? KtDeclaration) ?: (parent as? KtDeclaration)
