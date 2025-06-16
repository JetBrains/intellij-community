// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.ec4j.core.PropertyTypeRegistry
import org.ec4j.core.Resource
import org.ec4j.core.parser.*

class EditorConfigVerifyByCoreInspection : LocalInspectionTool() {
  override fun runForWholeFile(): Boolean = true

  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return null
    val text = document.text
    val resource = Resource.Resources.ofString(".editorconfig", text)
    val parser = EditorConfigParser.default_()
    val handler = ValidatingHandler(PropertyTypeRegistry.default_())
    val errorHandler = Ec4jErrorToProblemDescriptorHandler(file, text, manager, isOnTheFly)
    try {
      parser.parse(resource, handler, errorHandler)
    }
    catch (e: ParseException) {
      /* do nothing */
    }
    return errorHandler.problems.toTypedArray()
  }
}

/**
 * @see [EditorConfigLoadErrorHandler]
 */
private class Ec4jErrorToProblemDescriptorHandler(val file: PsiFile,
                                                  val text: String,
                                                  val inspectionManager: InspectionManager,
                                                  val isOnTheFly: Boolean) : ErrorHandler {
  val problems = mutableListOf<ProblemDescriptor>()

  override fun error(context: ParseContext, errorEvent: ErrorEvent) {
    when (errorEvent.errorType) {
      ErrorEvent.ErrorType.PROPERTY_ASSIGNMENT_MISSING -> {
        registerProblem(context, errorEvent)
      }
      ErrorEvent.ErrorType.EXPECTED_END_OF_INPUT,
      ErrorEvent.ErrorType.EXPECTED_STRING_CHARACTER,
      ErrorEvent.ErrorType.UNEXPECTED_END_OF_INPUT -> {
        registerProblem(context, errorEvent)
        // Stop parsing.
        // In the case of EXPECTED_STRING_CHARACTER, parser would get into an infinite loop. Not clear if the other two are safe.
        throw ParseException(errorEvent)
      }
      ErrorEvent.ErrorType.GLOB_NOT_CLOSED -> {
        // Stop parsing, otherwise string out-of-bounds exception will probably happen. This is a bug in ec4j.
        throw ParseException(errorEvent)
      }
      ErrorEvent.ErrorType.PROPERTY_VALUE_MISSING,
      ErrorEvent.ErrorType.INVALID_GLOB,
      ErrorEvent.ErrorType.INVALID_PROPERTY_VALUE,
      ErrorEvent.ErrorType.OTHER,
      null -> {
        /* ignore */
      }
    }
  }

  private fun registerProblem(context: ParseContext, errorEvent: ErrorEvent) {
    problems.add(createProblemDescriptor(context, errorEvent))
  }

  private fun createProblemDescriptor(context: ParseContext, errorEvent: ErrorEvent) =
    inspectionManager.createProblemDescriptor(
      file,
      text.tryToFindHighlightableOffset(context.location.offset).let { TextRange(it, it) },
      errorEvent.message,
      ProblemHighlightType.GENERIC_ERROR,
      isOnTheFly
    )
}

private fun String.tryToFindHighlightableOffset(offset: Int): Int {
  for (i in offset.coerceIn(this.indices) downTo 0) {
    if (this[i] != '\n') return i
  }
  return offset
}