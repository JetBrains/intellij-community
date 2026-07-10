// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.application.UI
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class LanguageTextFieldTest {
  @Test
  fun `text field with light PSI document can be created on strict UI dispatcher`() {
    val project = ProjectManager.getInstance().defaultProject
    val document = runBlocking(Dispatchers.UI) {
      LanguageTextField(FileTypes.PLAIN_TEXT.language, project, "value").document
    }

    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)

    assertThat(psiFile).isNotNull
    assertThat(psiFile!!.fileType).isEqualTo(FileTypes.PLAIN_TEXT)
    assertThat(document.text).isEqualTo("value")
  }
}
