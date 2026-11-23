// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class LightVirtualFileBuilderPsiTest {
  private companion object {
    val project = projectFixture()
  }

  @Test
  fun `findFile of a light file that skips the event system creates a non-physical file`() {
    val file = findFile(builder().skipEventSystem(true).build())

    assertFalse(file.viewProvider.supportsSendingPsiEvents())
    assertFalse(file.isPhysical)
  }

  @Test
  fun `findFile of a default light file creates a file that sends events`() {
    val file = findFile(builder().build())

    assertTrue(file.viewProvider.supportsSendingPsiEvents())
  }

  private fun builder(): LightVirtualFile.Builder =
    LightVirtualFile.builder().name("a.txt").fileType(PlainTextFileType.INSTANCE).content("hello")

  private fun findFile(virtualFile: LightVirtualFile): PsiFile =
    runReadActionBlocking { PsiManager.getInstance(project.get()).findFile(virtualFile)!! }
}
