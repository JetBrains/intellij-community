// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical.model

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

/**
 * Interface for logical elements which allow them provide information which psi elements they can represent
 */
@ApiStatus.Experimental
interface LogicalPsiDescription {

  /**
   * psi element which can be represented by this model or its children
   * @return null - if elements with this type are not supported
   */
  fun getSuitableElement(psiElement: PsiElement): PsiElement?

  /**
   * false - if this logical element can provide info about its children
   * true - if one needs to go deeper to collect full information
   */
  fun isAskChildren(): Boolean = false

}