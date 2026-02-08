// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.presentation

import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.psi.NavigatablePsiElement
import org.jetbrains.kotlin.K1Deprecation

@K1Deprecation
abstract class KtModuleSpecificListCellRenderer<T : NavigatablePsiElement> : PsiElementListCellRenderer<T>()