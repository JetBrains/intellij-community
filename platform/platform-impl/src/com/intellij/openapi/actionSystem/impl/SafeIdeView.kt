// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.ide.IdeView
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement

private val LOG = logger<SafeIdeView>()

fun safeIdeView(delegate: IdeView): IdeView = delegate as? SafeIdeView ?: SafeIdeView(delegate)

private class SafeIdeView(val delegate: IdeView) : IdeView {
  override fun selectElement(element: PsiElement) = delegate.selectElement(element)
  override fun getOrChooseDirectory(): PsiDirectory? = delegate.orChooseDirectory
  override fun getDirectories(): Array<out PsiDirectory> {
    val dirs = delegate.directories
    @Suppress("SENSELESS_COMPARISON")
    if (dirs.any { it == null }) {
      LOG.error("Array with null provided by " + delegate::class.java.getName())
      return dirs.filterNotNull().toTypedArray()
    }
    return dirs
  }
}