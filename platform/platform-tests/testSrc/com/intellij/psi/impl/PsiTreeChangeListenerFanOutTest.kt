// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.waitForAllDocumentsCommitted
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A regression test for IJPL-206874.
 *
 * `PsiManagerImpl.fireEvent` must notify the programmatic listeners and the extension point listeners independently.
 * The commit b33cd5931a05b joined the two guards with `&&`, so an empty list on one side silenced the other side.
 */
@TestApplication
class PsiTreeChangeListenerFanOutTest {
  private companion object {
    val project = projectFixture(openAfterCreation = true)
    val module = project.moduleFixture()
    val sourceRoot = module.sourceRootFixture()

    /** The same point as [PsiTreeChangeListener.EP], but in the form that [ExtensionTestUtil] accepts. */
    val EP_NAME: ExtensionPointName<PsiTreeChangeListener> = ExtensionPointName("com.intellij.psi.treeChangeListener")
  }

  val file = sourceRoot.psiFileFixture("A.java", "class A {}")

  private class CountingListener : PsiTreeChangeAdapter() {
    val count: AtomicInteger = AtomicInteger()

    override fun childrenChanged(event: PsiTreeChangeEvent) {
      count.incrementAndGet()
    }
  }

  @Test
  fun `a programmatic listener gets the events when the extension point is empty`(@TestDisposable testDisposable: Disposable) {
    ExtensionTestUtil.maskExtensions(EP_NAME, emptyList(), testDisposable, true, project.get())

    val listener = CountingListener()
    PsiManagerEx.getInstanceEx(project.get()).addPsiTreeChangeListener(listener, testDisposable)

    changeTheFile()

    assertTrue(listener.count.get() > 0, "The programmatic listener got no event")
  }

  @Test
  fun `an extension point listener gets the events`(@TestDisposable testDisposable: Disposable) {
    val listener = CountingListener()
    ExtensionTestUtil.maskExtensions(EP_NAME, listOf<PsiTreeChangeListener>(listener), testDisposable, true, project.get())

    changeTheFile()

    assertTrue(listener.count.get() > 0, "The extension point listener got no event")
  }

  private fun changeTheFile() {
    invokeAndWaitIfNeeded {
      val psiFile = file.get()
      val document = PsiDocumentManager.getInstance(project.get()).getDocument(psiFile)!!
      WriteCommandAction.runWriteCommandAction(project.get()) { document.insertString(0, "/*x*/") }
      waitForAllDocumentsCommitted(10, TimeUnit.SECONDS)
    }
  }
}
