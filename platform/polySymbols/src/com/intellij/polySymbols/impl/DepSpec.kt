// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.model.Pointer
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer

internal sealed interface DepSpec<T : Any> {
  fun currentValue(): T
  fun toPointer(): Pointer<out T>

  class FromPsiElement<T : PsiElement>(val element: T) : DepSpec<T> {
    override fun currentValue(): T = element
    override fun toPointer(): Pointer<out T> {
      return element.createSmartPointer()
    }
  }

  class FromGenericObject<T : Any>(val `object`: T, val pointerProvider: (T) -> Pointer<out T>) : DepSpec<T> {
    override fun currentValue(): T = `object`
    override fun toPointer(): Pointer<out T> = pointerProvider(`object`)
  }
}
