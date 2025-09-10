// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.highlighting.fixture.highlightingFixture
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFalse

@TestApplication
class DaemonCodeAnalyzerTest {
  private companion object {
    val project = projectFixture(openAfterCreation = true)
    val module = project.moduleFixture()
    val sourceRoot = module.sourceRootFixture()
    val file = sourceRoot.psiFileFixture("A.java", "class A {}")
  }

  private val localEditor = file.editorFixture()

  private val highlighting = localEditor.highlightingFixture()

  @Test
  fun `highlighting gets canceled on pending write action`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val analyzer = highlighting.get()
    val listenerInvoked = AtomicBoolean(false)
    project.get().messageBus.connect(disposable).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object: DaemonCodeAnalyzer.DaemonListener {
      override fun daemonStarting(fileEditors: Collection<FileEditor>) {
        listenerInvoked.set(true)
      }
    })
    launch(Dispatchers.Default) {
      backgroundWriteAction {  }
    }
    while (!ApplicationManagerEx.getApplicationEx().isWriteActionPending) {
      Thread.sleep(10)
    }
    analyzer.restart()
    Thread.sleep(DaemonCodeAnalyzerSettings.getInstance().autoReparseDelay.times(2).toLong())
    UIUtil.dispatchAllInvocationEvents()
    assertFalse(listenerInvoked.get())
  }
}