// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions

import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun interface PsiCollector<T : PsiElement> {
  fun collectTargetPsi(fileContent: CharSequence, fileType: FileType): List<T>
}