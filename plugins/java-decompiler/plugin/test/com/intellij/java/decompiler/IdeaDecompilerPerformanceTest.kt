// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.decompiler

import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.util.SystemProperties
import com.intellij.util.currentJavaVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.java.decompiler.DecompilerPreset
import org.jetbrains.java.decompiler.IdeaDecompiler
import org.jetbrains.java.decompiler.IdeaDecompilerSettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@PerformanceUnitTest
@StressTestApplication
class IdeaDecompilerPerformanceTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @AfterEach
  fun tearDown(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    withContext(Dispatchers.EDT) { FileEditorManagerEx.getInstanceEx(myFixture.project).closeAllFiles() }
    EditorHistoryManager.getInstance(myFixture.project).removeAllFiles()

    val defaultState = IdeaDecompilerSettings.State.fromPreset(DecompilerPreset.HIGH)
    IdeaDecompilerSettings.getInstance().loadState(defaultState)
  }

  @Test
  fun testPerformance() {
    val decompiler = IdeaDecompiler()
    val jrt = currentJavaVersion().feature >= 9
    val base = if (jrt) "jrt://${SystemProperties.getJavaHome()}!/java.desktop/" else "jar://${SystemProperties.getJavaHome()}/lib/rt.jar!/"
    val file = VirtualFileManager.getInstance().findFileByUrl(base + "javax/swing/JTable.class")!!
    Benchmark.newBenchmark("decompiling JTable.class") { decompiler.getText(file) }.start()
  }
}