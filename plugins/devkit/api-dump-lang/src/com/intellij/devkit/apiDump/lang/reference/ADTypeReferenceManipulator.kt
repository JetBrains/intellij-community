// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.reference

import com.intellij.devkit.apiDump.lang.psi.ADTypeReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulator

internal class ADTypeReferenceManipulator : ElementManipulator<ADTypeReference> {
  override fun handleContentChange(element: ADTypeReference, range: TextRange, newContent: String?): ADTypeReference? =
    element

  override fun handleContentChange(element: ADTypeReference, newContent: String?): ADTypeReference? =
    throw UnsupportedOperationException()

  override fun getRangeInElement(element: ADTypeReference): TextRange =
    throw UnsupportedOperationException()
}