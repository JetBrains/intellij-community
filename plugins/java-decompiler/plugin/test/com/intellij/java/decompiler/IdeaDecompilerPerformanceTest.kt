// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.decompiler

import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.util.SystemProperties
import com.intellij.util.lang.JavaVersion
import org.jetbrains.java.decompiler.DecompilerPreset
import org.jetbrains.java.decompiler.IdeaDecompiler
import org.jetbrains.java.decompiler.IdeaDecompilerSettings

class IdeaDecompilerPerformanceTest : LightJavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.testDataPath = "${PluginPathManager.getPluginHomePath("java-decompiler")}/plugin/testData"
  }

  override fun tearDown() {
    try {
      FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
      EditorHistoryManager.getInstance(project).removeAllFiles()

      val defaultState = IdeaDecompilerSettings.State.fromPreset(DecompilerPreset.HIGH)
      IdeaDecompilerSettings.getInstance().loadState(defaultState)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testPerformance() {
    val decompiler = IdeaDecompiler()
    val jrt = JavaVersion.current().feature >= 9
    val base = if (jrt) "jrt://${SystemProperties.getJavaHome()}!/java.desktop/" else "jar://${SystemProperties.getJavaHome()}/lib/rt.jar!/"
    val file = VirtualFileManager.getInstance().findFileByUrl(base + "javax/swing/JTable.class")!!
    Benchmark.newBenchmark("decompiling JTable.class") { decompiler.getText(file) }.start()
  }
}