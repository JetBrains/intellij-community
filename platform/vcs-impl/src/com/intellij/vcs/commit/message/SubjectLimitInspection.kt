// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.psi.PsiFile
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindIntValue
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class SubjectLimitInspection : BaseCommitMessageInspection() {
  @JvmField
  var RIGHT_MARGIN: Int = 72

  override fun getDisplayName(): @Nls String {
    return VcsBundle.message("inspection.SubjectLimitInspection.display.name")
  }

  override fun Panel.createOptions(project: Project, disposable: Disposable): Boolean {
    row(VcsBundle.message("settings.commit.message.right.margin.label")) {
      spinner(0..10000)
        .bindIntValue(::RIGHT_MARGIN)
    }
    return false
  }

  override fun checkFile(file: PsiFile, document: Document, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    val problemText = VcsBundle.message("commit.message.inspection.message.subject.should.not.exceed.characters", RIGHT_MARGIN)
    val descriptor = checkRightMargin(file, document, manager, isOnTheFly, 0, RIGHT_MARGIN, problemText) ?: return null
    return listOf(descriptor).toTypedArray()
  }
}
