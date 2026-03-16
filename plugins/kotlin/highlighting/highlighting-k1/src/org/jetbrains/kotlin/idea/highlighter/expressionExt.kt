// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.psi.KtNamedFunction

// TODO: clean up!!!

@K1Deprecation
val PsiElement.isAnonymousFunction: Boolean get() = this is KtNamedFunction && isAnonymousFunction

@K1Deprecation
val KtNamedFunction.isAnonymousFunction: Boolean get() = nameIdentifier == null