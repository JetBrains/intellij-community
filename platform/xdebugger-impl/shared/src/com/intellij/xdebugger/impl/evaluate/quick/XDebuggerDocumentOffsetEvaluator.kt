// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate.quick

import com.intellij.openapi.editor.Document
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator.XEvaluationCallback
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XDebuggerDocumentOffsetEvaluator {
  fun evaluate(document: Document, offset: Int, hintType: ValueHintType, callback: XEvaluationCallback)
}
