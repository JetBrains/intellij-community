// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.propertyBased

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.jetCheck.ImperativeCommand.Environment

class CommitDocumentAction(file: PsiFile) : ActionOnFile(file) {

  override fun performCommand(env: Environment) {
    val document = file.viewProvider.document ?: return
    PsiDocumentManager.getInstance(project).commitDocument(document)
  }
}
