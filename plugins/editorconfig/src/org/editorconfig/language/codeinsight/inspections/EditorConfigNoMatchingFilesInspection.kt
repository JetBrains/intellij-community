// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VfsUtilCore
import org.editorconfig.core.EditorConfigAutomatonBuilder.getCachedHeaderRunAutomaton
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveSectionQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigVisitor
import org.editorconfig.language.util.EditorConfigPsiTreeUtil

class EditorConfigNoMatchingFilesInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : EditorConfigVisitor() {
    override fun visitHeader(header: EditorConfigHeader) {
      if (!header.isValidGlob) return
      val runAutomaton = getCachedHeaderRunAutomaton(header)
      val folder = EditorConfigPsiTreeUtil.getOriginalFile(header.containingFile)?.virtualFile?.parent ?: return
      var pass = 0
      val found = !VfsUtilCore.iterateChildrenRecursively(folder, null) {
        pass += 1
        if (pass % CancellationCheckFrequency == 0) ProgressManager.checkCanceled()
        !(it.isValid && runAutomaton.run(it.path))
      }

      if (found) return
      val message = EditorConfigBundle.get("inspection.no-matching-files.message", folder.name)
      holder.registerProblem(header, message, ProblemHighlightType.LIKE_UNUSED_SYMBOL, EditorConfigRemoveSectionQuickFix())
    }
  }

  private companion object {
    private const val CancellationCheckFrequency = 20
  }
}
