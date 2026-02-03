// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.editorconfig.common.syntax.psi.EditorConfigDescribableElement
import com.intellij.editorconfig.common.syntax.psi.EditorConfigIdentifierElement
import com.intellij.editorconfig.common.syntax.psi.EditorConfigVisitor
import com.intellij.openapi.application.ApplicationNamesInfo
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveDeprecatedElementQuickFix
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveOptionQuickFix
import org.editorconfig.language.schema.descriptors.getDescriptor
import java.text.MessageFormat

class EditorConfigDeprecatedDescriptorInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): EditorConfigVisitor = object : EditorConfigVisitor() {
    override fun visitDescribableElement(element: EditorConfigDescribableElement) {
      val descriptor = element.getDescriptor(true) ?: return
      val deprecation = descriptor.deprecation ?: return
      val message = MessageFormat.format(deprecation, ApplicationNamesInfo.getInstance().fullProductName)
      holder.registerProblem(
        element,
        message,
        ProblemHighlightType.LIKE_DEPRECATED,
        EditorConfigRemoveDeprecatedElementQuickFix(),
        EditorConfigRemoveOptionQuickFix()
      )
    }

    override fun visitIdentifierElement(element: EditorConfigIdentifierElement) =
      visitDescribableElement(element)
  }
}
