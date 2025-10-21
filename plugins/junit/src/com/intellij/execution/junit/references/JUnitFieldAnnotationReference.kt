// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.references

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.uast.UField

abstract class JUnitFieldAnnotationReference(element: PsiLanguageInjectionHost) : BaseJunitAnnotationReference<PsiField, UField>(element) {
  override fun isPsiType(element: PsiElement): Boolean = element is PsiField
  override fun getPsiElementsByName(directClass: PsiClass, name: String, checkBases: Boolean): Array<PsiField> = directClass.findFieldByName(name, checkBases)?.let { arrayOf(it) } ?: PsiField.EMPTY_ARRAY
  override fun uType(): Class<UField> = UField::class.java
  override fun getAll(directClass: PsiClass): Array<PsiField> = directClass.allFields
  override fun toTypedPsiArray(collection: Collection<PsiField>): Array<PsiField> = collection.toTypedArray()
}