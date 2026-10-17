// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.openapi.project.Project
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.callUnderOwnClassLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * A [DisposedPsiManagerCheck] names the stack that created it when its project is disposed,
 * and it keeps no class loader of the code that created it.
 */
internal class DisposedPsiManagerCheckTest {
  @Test
  fun `a check keeps no class loader of the code that created it`() {
    val project = Mockito.mock(Project::class.java)
    val call = callUnderOwnClassLoader { DisposedPsiManagerCheck(project) }

    call.loader.ensureCollected()
    call.result.performCheck()
  }

  @Test
  fun `the check of a disposed project names the stack that created the check`() {
    val project = Mockito.mock(Project::class.java)
    Mockito.`when`(project.isDisposed).thenReturn(true)
    val check = createCheck(project)

    val error = LoggedErrorProcessor.executeAndReturnLoggedError { check.performCheck() }
    assertThat(error.message).startsWith("Disposed\n").contains("${DisposedPsiManagerCheckTest::class.java.name}.createCheck(")
  }

  private fun createCheck(project: Project): DisposedPsiManagerCheck = DisposedPsiManagerCheck(project)
}
