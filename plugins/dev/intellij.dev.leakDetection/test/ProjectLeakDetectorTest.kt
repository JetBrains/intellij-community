// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.leakDetection

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.testFramework.LightPlatformTestCase

class ProjectLeakDetectorTest : LightPlatformTestCase() {
  fun testBuildReportFormatsEntries() {
    val leaks = listOf(
      LeakInfo(LeakKind.PROJECT, "com.example.FooProject", 0x1234, "Project Foo", "created in test", null, "root -> Foo"),
      LeakInfo(LeakKind.EDITOR, "com.example.BarEditor", 0xabcd, "Editor Bar", null, 12_345L, "root -> Bar"),
    )
    val report = LeakReporter.buildReport(leaks)
    assertTrue(report.contains("2 leaked instance(s)"))
    assertTrue(report.contains("com.example.FooProject@1234"))
    assertTrue(report.contains("retained for: 12345 ms after disposal"))
    assertTrue(report.contains("created at: created in test"))
  }

  fun testFindsLeakedDisposedEditor() {
    try {
      val factory = EditorFactory.getInstance()
      val editor = factory.createEditor(factory.createDocument("leaked editor content"))
      val editorHash = System.identityHashCode(editor)
      factory.releaseEditor(editor) // marks it disposed and stamps the disposal timestamp
      leakedEditor = editor // hold via a static field so it is reachable from the loaded-classes-statics GC root

      // threshold 0: any disposed-but-retained editor qualifies, regardless of how recently it was released
      val leaks = ProjectLeakDetector(editorStaleThresholdMs = 0).detect()
      assertTrue("expected the leaked editor to be detected, got: $leaks",
                 leaks.any { it.kind == LeakKind.EDITOR && it.identityHashCode == editorHash })
    }
    finally {
      leakedEditor = null
    }
  }

  companion object {
    @JvmStatic
    @Suppress("unused")
    var leakedEditor: Editor? = null
  }
}
