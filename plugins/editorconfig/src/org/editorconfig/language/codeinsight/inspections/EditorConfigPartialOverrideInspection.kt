// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigVisitor
import org.editorconfig.language.util.headers.EditorConfigOverriddenHeaderSearcher
import org.editorconfig.language.util.headers.EditorConfigOverridingHeaderSearcher

class EditorConfigPartialOverrideInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : EditorConfigVisitor() {
    override fun visitHeader(header: EditorConfigHeader) {
      if (!header.isValidGlob) return
      val isPartiallyOverriding = EditorConfigOverriddenHeaderSearcher(false).findMatchingHeaders(header).any { it.isPartial }
      val isPartiallyOverridden = EditorConfigOverridingHeaderSearcher().findMatchingHeaders(header).any { it.isPartial }
      if (!isPartiallyOverridden && !isPartiallyOverriding) return
      val message = EditorConfigBundle["inspection.header.partially.overridden.message"]
      holder.registerProblem(header, message)
    }
  }
}
