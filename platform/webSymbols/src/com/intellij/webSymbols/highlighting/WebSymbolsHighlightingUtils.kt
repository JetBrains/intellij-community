// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.highlighting

import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.NlsSafe

private val isDebugMode = ApplicationManager.getApplication().isUnitTestMode

fun AnnotationHolder.newSilentAnnotationWithDebugInfo(severity: HighlightSeverity, @NlsSafe debugName: String): AnnotationBuilder =
  if (isDebugMode)
    newAnnotation(severity, debugName)
  else
    newSilentAnnotation(severity)