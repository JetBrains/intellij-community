// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.leakDetection

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndGet
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URLClassLoader

@TestApplication
class ProjectLeakDetectorTest {
  @Test
  fun testBuildReportFormatsEntries() {
    val leaks = listOf(
      LeakInfo(LeakKind.PROJECT, "com.example.FooProject", 0x1234, "Project Foo", "created in test", null, "root -> Foo"),
      LeakInfo(LeakKind.EDITOR, "com.example.BarEditor", 0xabcd, "Editor Bar", null, 12_345L, "root -> Bar"),
    )
    val report = LeakDetectionRunner.getInstance().reporter().buildReport (leaks)
    assertTrue(report.contains("2 leaked instance(s)"))
    assertTrue(report.contains("com.example.FooProject@1234"))
    assertTrue(report.contains("retained for: 12345 ms after disposal"))
    assertTrue(report.contains("created at: created in test"))
  }

  @Test
  fun testFindsLeakedDisposedEditor() {
    try {
      val editorHash = runInEdtAndGet {
        val factory = EditorFactory.getInstance()
        val editor = factory.createEditor(factory.createDocument("leaked editor content"))
        val hash = System.identityHashCode(editor)
        // Production tracks editors via LeakCandidateEditorListener; the plugin's listeners are not installed in
        // this bare application, so feed the registry directly to arm the detector's pre-check.
        LeakCandidateRegistry.getInstance().register(editor)
        factory.releaseEditor(editor) // marks it disposed and stamps the disposal timestamp
        leakedEditor = editor // hold via a static field so the editor stays reachable once this test's Class is walked
        hash
      }

      // threshold 0: any disposed-but-retained editor qualifies, regardless of how recently it was released
      val leaks = timeoutRunBlocking { ProjectLeakDetector(editorStaleThresholdMs = 0).detect() }
      assertTrue(leaks.any { it.kind == LeakKind.EDITOR && it.identityHashCode == editorHash },
                 "expected the leaked editor to be detected, got: $leaks")
    }
    finally {
      leakedEditor = null
    }
  }

  @Test
  fun testRegistryReportsRetainedDisposedInstances() {
    // The detector's cheap pre-check relies on this: a live instance must not trigger the walk, a retained
    // disposed one must. Use a fresh registry (not the app service) so the assertions are independent of other tests.
    runInEdtAndGet {
      val factory = EditorFactory.getInstance()
      val registry = LeakCandidateRegistry()
      val aliveEditor = factory.createEditor(factory.createDocument("alive editor content"))
      try {
        registry.register(aliveEditor)
        assertFalse(registry.hasRetainedDisposedInstances(),
                    "a live editor must not be reported as a retained disposed instance")

        val disposedEditor = factory.createEditor(factory.createDocument("disposed editor content"))
        registry.register(disposedEditor)
        factory.releaseEditor(disposedEditor)
        assertTrue(registry.hasRetainedDisposedInstances(),
                   "a retained disposed editor must be reported")
      }
      finally {
        factory.releaseEditor(aliveEditor)
      }
    }
  }

  @Test
  fun testRootsIncludeAwtStaticContainers() {
    // AWT keeps live windows/focus state in static containers that are not reachable through IdeEventQueue.
    val labels = ProjectLeakDetector().buildRoots().values
    assertTrue("java.awt.Window.getWindows()" in labels,
               "expected a java.awt.Window.getWindows() root, got: $labels")
    assertTrue("java.awt.Frame.getFrames()" in labels,
               "expected a java.awt.Frame.getFrames() root, got: $labels")
    assertTrue("KeyboardFocusManager.getCurrentKeyboardFocusManager()" in labels,
               "expected a KeyboardFocusManager root, got: $labels")
  }

  @Test
  fun testStaticsRootScansMultipleClassloaders() {
    // The broadened detector covers static fields behind many classloaders, not just this plugin's. Verify the
    // classloader set directly (whether `ClassLoader.classes` is reflectively readable is JDK-dependent, so we do
    // not assert on the resulting roots): it must include this plugin's loader and the current thread's context
    // loader, and span more than one loader.
    val marker = URLClassLoader(emptyArray(), null)
    val previous = Thread.currentThread().contextClassLoader
    Thread.currentThread().contextClassLoader = marker
    try {
      val loaders = ProjectLeakDetector().staticsRootClassLoaders()
      assertTrue(loaders.size >= 2, "expected more than one classloader to be scanned, got: $loaders")
      assertTrue(ProjectLeakDetector::class.java.classLoader in loaders,
                 "expected this plugin's classloader to be scanned, got: $loaders")
      assertTrue(marker in loaders,
                 "expected the current thread's context classloader to be scanned, got: $loaders")
    }
    finally {
      Thread.currentThread().contextClassLoader = previous
    }
  }

  companion object {
    @JvmStatic
    @Suppress("unused")
    var leakedEditor: Editor? = null
  }
}
