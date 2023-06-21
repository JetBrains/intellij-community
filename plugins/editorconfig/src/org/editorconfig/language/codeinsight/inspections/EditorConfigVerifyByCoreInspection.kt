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
  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return null
    val resource = Resource.Resources.ofString(".editorconfig", document.text)
    val parser = EditorConfigParser.default_()
    val handler = ValidatingHandler(PropertyTypeRegistry.default_())
    val errorHandler = Ec4jErrorToProblemDescriptorHandler(file, manager)
    parser.parse(resource, handler, errorHandler)
    return errorHandler.problems.toTypedArray()
  }
}

private class Ec4jErrorToProblemDescriptorHandler(val file: PsiFile, val inspectionManager: InspectionManager) : ErrorHandler {
  val problems = mutableListOf<ProblemDescriptor>()

  override fun error(context: ParseContext, errorEvent: ErrorEvent) {
    when (errorEvent.errorType) {
      ErrorEvent.ErrorType.PROPERTY_ASSIGNMENT_MISSING -> {
        problems.add(inspectionManager.createProblemDescriptor(
          file,
          TextRange(context.location.offset, context.location.offset),
          errorEvent.message,
          ProblemHighlightType.GENERIC_ERROR,
          true
        ))
      }
      else -> {
        /* do not report */
      }
    }
  }
}