// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.psi.PsiFile
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class SubjectLimitInspection : BaseCommitMessageInspection() {
  @JvmField
  var RIGHT_MARGIN: Int = 72

  override fun getDisplayName(): @Nls String {
    return VcsBundle.message("inspection.SubjectLimitInspection.display.name")
  }

  override fun createOptionsConfigurable(): ConfigurableUi<Project?> {
    return SubjectLimitInspectionOptions(this)
  }

  override fun checkFile(file: PsiFile, document: Document, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    val problemText = VcsBundle.message("commit.message.inspection.message.subject.should.not.exceed.characters", RIGHT_MARGIN)
    val descriptor = checkRightMargin(file, document, manager, isOnTheFly, 0, RIGHT_MARGIN, problemText) ?: return null
    return listOf(descriptor).toTypedArray()
  }
}
