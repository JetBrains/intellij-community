// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CancellationException
import java.util.function.Predicate

@TestApplication
class DaemonCodeAnalyzerFailedCreationTest {
  private val project = projectFixture()

  @Test
  fun `a daemon whose constructor fails leaves nothing in the Disposer tree`() {
    val project = project.get()
    assertNull(project.getServiceIfCreated(DaemonCodeAnalyzer::class.java))
    // the daemon constructor requests this service before its first Disposer registration
    DependencyValidationManager.getInstance(project)
    val daemonsBefore = daemonsInDisposerTree()

    // the project close cancels the project scope before it disposes the services
    (project as ComponentManagerEx).getCoroutineScope().cancel()
    assertThrows<CancellationException> { DaemonCodeAnalyzer.getInstance(project) }

    Disposer.getTree().assertNoReferenceKeptInTree(Predicate { it is DaemonCodeAnalyzerImpl && it !in daemonsBefore })
  }
}

private fun daemonsInDisposerTree(): Set<Any> {
  val daemons = ReferenceOpenHashSet<Any>()
  Disposer.getTree().assertNoReferenceKeptInTree(Predicate {
    if (it is DaemonCodeAnalyzerImpl) {
      daemons.add(it)
    }
    false
  })
  return daemons
}
