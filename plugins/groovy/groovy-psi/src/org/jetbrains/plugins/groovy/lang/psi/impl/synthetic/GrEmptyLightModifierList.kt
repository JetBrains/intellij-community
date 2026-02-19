// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation

class GrEmptyLightModifierList(parent: PsiElement): GrLightModifierList(parent) {
  override fun getModifierFlags(): Int = 0

  override fun getModifiers(): Array<out PsiElement?> = emptyArray()

  override fun getModifier(name: @NonNls String): PsiElement? = null

  override fun hasExplicitVisibilityModifiers(): Boolean = false

  override fun getAnnotations(): Array<out GrAnnotation?> = emptyArray()

  override fun getApplicableAnnotations(): Array<out PsiAnnotation?> = emptyArray()

  override fun findAnnotation(qualifiedName: @NonNls String): PsiAnnotation? = null

  override fun addAnnotation(qualifiedName: @NonNls String): GrLightAnnotation {
    throw UnsupportedOperationException()
  }

  override fun hasModifierProperty(name: @NonNls String): Boolean = false

  override fun hasExplicitModifier(name: @NonNls String): Boolean = false

  override fun setModifierProperty(name: @NonNls String, value: Boolean) {
    throw IncorrectOperationException()
  }

  override fun checkSetModifierProperty(name: @NonNls String, value: Boolean) {
   throw IncorrectOperationException()
  }

  override fun getRawAnnotations(): Array<out GrAnnotation?> = emptyArray()

  override fun toString(): String = "Empty modifier list"
}