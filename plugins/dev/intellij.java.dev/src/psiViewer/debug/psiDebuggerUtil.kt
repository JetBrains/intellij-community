// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.dev.psiViewer.debug

import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.openapi.util.TextRange
import com.sun.jdi.IntegerValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.StringReference
import com.sun.jdi.Value

internal const val GET_CONTAINING_FILE = "getContainingFile"

internal fun DebugProcess.invokeMethod(reference: ObjectReference, methodName: String, evalContext: EvaluationContext): Value? {
  val method = DebuggerUtils.findMethod(reference.referenceType(), methodName, null) ?: return null
  return invokeMethod(evalContext, reference, method, emptyList())
}

private const val GET_TEXT = "getText"

internal fun ObjectReference.getText(debugProcess: DebugProcess, context: EvaluationContext): String? {
  val stringObj = debugProcess.invokeMethod(this, GET_TEXT, context)
  return DebuggerUtils.getValueAsString(context, stringObj) ?: return null
}

private const val GET_TEXT_RANGE = "getTextRange"
private const val GET_START_OFFSET = "getStartOffset"
private const val GET_END_OFFSET = "getEndOffset"

internal fun ObjectReference.getTextRange(debugProcess: DebugProcess, context: EvaluationContext): TextRange? {
  val textRangeObj = debugProcess.invokeMethod(this, GET_TEXT_RANGE, context) as? ObjectReference ?: return null
  val startOffset = debugProcess.invokeMethod(textRangeObj, GET_START_OFFSET, context) as? IntegerValue ?: return null
  val endOffset = debugProcess.invokeMethod(textRangeObj, GET_END_OFFSET, context) as? IntegerValue ?: return null
  return TextRange(startOffset.value(), endOffset.value())
}

private const val GET_LANGUAGE = "getLanguage"
private const val GET_ID = "getID"

fun ObjectReference.getLanguageId(debugProcess: DebugProcess, context: EvaluationContext): String? {
  val languageObj = debugProcess.invokeMethod(this, GET_LANGUAGE, context) as? ObjectReference ?: return null
  val stringObj = debugProcess.invokeMethod(languageObj, GET_ID, context) as? StringReference ?: return null
  return DebuggerUtils.getValueAsString(context, stringObj) ?: return null
}