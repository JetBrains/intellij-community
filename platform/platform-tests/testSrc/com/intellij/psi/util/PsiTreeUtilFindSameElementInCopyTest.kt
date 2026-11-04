// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.DummyHolderFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import kotlin.io.path.createDirectory

@TestApplication
internal class PsiTreeUtilFindSameElementInCopyTest {
  private companion object {
    private val project = projectFixture(openAfterCreation = true)
  }

  private val tempDir by tempPathFixture()

  @Test
  fun `walk stops at the file when the copy has another file class`(): Unit = timeoutRunBlocking {
    val directory = tempDir.resolve("dir").createDirectory()
    repeat(10) { Files.writeString(directory.resolve("sibling$it.txt"), "") }
    val path = directory.resolve("z.txt")
    Files.writeString(path, "text")
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
    runReadActionBlocking {
      val psiManager = PsiManager.getInstance(project.get())
      val file = psiManager.findFile(virtualFile)!!
      val copy = DummyHolderFactory.createHolder(psiManager, null)
      val e = assertThrows<IllegalStateException> { PsiTreeUtil.findSameElementInCopy(file, copy) }
      assertThat(e.message).contains("topmost parent class: ${file.javaClass}")
    }
  }
}
