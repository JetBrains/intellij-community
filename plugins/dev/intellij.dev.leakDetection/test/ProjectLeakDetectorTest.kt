// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.leakDetection

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndGet
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URLClassLoader
import kotlin.coroutines.EmptyCoroutineContext

@TestApplication
class ProjectLeakDetectorTest {
  @Test
  fun testBuildReportFormatsEntries() {
    val leaks = listOf(
      LeakInfo(LeakKind.PROJECT, "com.example.FooProject", 0x1234, "Project Foo", "created in test", null,
               listOf(step(" (root)", "com.example.Root"))),
      LeakInfo(LeakKind.EDITOR, "com.example.BarEditor", 0xabcd, "Editor Bar", null, 12_345L,
               listOf(step(" (root)", "com.example.Root"))),
    )
    val report = LeakDetectionRunner.getInstance().reporter().buildInteractiveReport(leaks)
    assertTrue(report.contains("2 leaked instance(s)"))
    assertTrue(report.contains("com.example.FooProject@1234"))
    assertTrue(report.contains("retained for: 12345 ms after disposal"))
    assertTrue(report.contains("created at: created in test"))
  }

  @Test
  fun testBuildReportSquashesStructurallyEqualPaths() {
    // Two editors retained through the same chain, differing only by field values and an array index: they must be
    // squashed into a single entry whose reference path is printed once.
    val leaks = listOf(
      LeakInfo(LeakKind.EDITOR, "com.example.BarEditor", 0xaaaa, "Editor A", null, 100L,
               listOf(step("com.example.Tracker.field", "com.example.Tracker", "com.example.Tracker@1111"),
                      step("[1]", "[Ljava.lang.Object;"))),
      LeakInfo(LeakKind.EDITOR, "com.example.BarEditor", 0xbbbb, "Editor B", null, 200L,
               listOf(step("com.example.Tracker.field", "com.example.Tracker", "com.example.Tracker@2222"),
                      step("[2]", "[Ljava.lang.Object;"))),
    )
    val report = LeakDetectionRunner.getInstance().reporter().buildInteractiveReport(leaks)

    assertTrue(report.contains("2 leaked instance(s) in 1 distinct retention path(s)"), report)
    assertTrue(report.contains("2 instances sharing this path"), report)
    assertTrue(report.contains("@aaaa"), report)
    assertTrue(report.contains("@bbbb"), report)
    // The shared reference path is rendered exactly once, not repeated per instance.
    assertEquals(1, report.split("com.example.Tracker.field").size - 1, report)
  }

  @Test
  fun testBuildReportKeepsDistinctPathsSeparate() {
    // Same class but genuinely different reference paths must not be squashed.
    val leaks = listOf(
      LeakInfo(LeakKind.EDITOR, "com.example.BarEditor", 0xaaaa, "Editor A", null, 100L,
               listOf(step("com.example.One.a", "com.example.One"))),
      LeakInfo(LeakKind.EDITOR, "com.example.BarEditor", 0xbbbb, "Editor B", null, 200L,
               listOf(step("com.example.Two.b", "com.example.Two"))),
    )
    val report = LeakDetectionRunner.getInstance().reporter().buildInteractiveReport(leaks)

    assertFalse(report.contains("distinct retention path(s)"), report)
    assertTrue(report.contains("com.example.BarEditor@aaaa"), report)
    assertTrue(report.contains("com.example.BarEditor@bbbb"), report)
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
  fun testFindsLeakedDisposedEditorWithoutGc() {
    try {
      val editorHash = runInEdtAndGet {
        val factory = EditorFactory.getInstance()
        val editor = factory.createEditor(factory.createDocument("leaked editor content without GC"))
        LeakCandidateRegistry.getInstance().register(editor)
        factory.releaseEditor(editor)
        leakedEditor = editor
        System.identityHashCode(editor)
      }

      val leaks = timeoutRunBlocking { ProjectLeakDetector(editorStaleThresholdMs = 0).detect(forceGc = false) }
      assertTrue(leaks.any { it.kind == LeakKind.EDITOR && it.identityHashCode == editorHash },
                 "expected the leaked editor to be detected without a GC, got: $leaks")
    }
    finally {
      leakedEditor = null
    }
  }

  @Test
  fun testBuildReportContainsDisposerParentChain() {
    val leak = LeakInfo(LeakKind.PROJECT, "com.example.FooProject", 0x1234, "Project Foo", null, null,
                        listOf(step("com.example.Holder.project", "com.example.FooProject")),
                        listOf(ObjectRendering("com.example.Foo", "Foo")))
    val report = LeakDetectionRunner.getInstance().reporter().buildInteractiveReport(listOf(leak))

    assertTrue(report.contains("  Disposer parent chain:\n    Foo (com.example.Foo)\n      <- ROOT"), report)
  }

  @Suppress("RAW_SCOPE_CREATION")
  @Test
  fun testSameLeakIsSelectedForReportOnce() {
    val service = IdleLeakDetectionService(CoroutineScope(EmptyCoroutineContext))
    val first = LeakInfo(LeakKind.EDITOR, "com.example.BarEditor", 0xaaaa, "Editor A", null, 100L,
                         listOf(step("com.example.Holder.tracker", "com.example.Tracker", "com.example.Tracker@1111")))
    // Differs only by the identity hash, so it has the same signature.
    val same = first.copy(identityHashCode = 0xbbbb,
                          referencePath = listOf(step("com.example.Holder.tracker", "com.example.Tracker", "com.example.Tracker@2222")))
    val other = first.copy(referencePath = listOf(step("com.example.Holder.tracker", "com.example.Other")))

    assertEquals(listOf(listOf(first, same)), service.selectNotReported(listOf(first, same)))
    assertEquals(listOf(listOf(other)), service.selectNotReported(listOf(same, other)))
    assertEquals(emptyList<List<LeakInfo>>(), service.selectNotReported(listOf(first, same, other)))
  }

  @Test
  fun testLeakSignatureIgnoresFieldValues() {
    val first = LeakInfo(LeakKind.EDITOR, "com.example.BarEditor", 0xaaaa, "Editor A", null, 100L,
                         listOf(step("com.example.Holder.editor", "com.example.Editor", "EditorImpl[/AirPrompt-1176a009.md]"),
                                step("com.example.Draft\$\$Lambda/0x00001c0005d5f000.arg\$1", "com.example.Project", "Project(name=secret)"),
                                step(" (root)", "com.example.Root")))
    val second = first.copy(referencePath = listOf(
      step("com.example.Holder.editor", "com.example.Editor", "EditorImpl[/AirPrompt-94b19292.md]"),
      step("com.example.Draft\$\$Lambda/0x00001c0005971800.arg\$1", "com.example.Project", "Project(name=other)"),
      step(" (root)", "com.example.Root")))
    val other = first.copy(referencePath = listOf(step("com.example.Holder.other", "com.example.Editor")) + first.referencePath.drop(1))

    assertEquals(leakSignature(first), leakSignature(second))
    assertFalse(leakSignature(first) == leakSignature(other))
  }

  private val leakedProject = LeakInfo(LeakKind.PROJECT,
                                       "com.intellij.openapi.project.impl.ProjectImpl",
                                       0x1234,
                                       "Project(name=matterhorn-secret, containerState=DISPOSE_COMPLETED)",
                                       "created by matterhorn-secret",
                                       null,
                                       listOf(step("com.example.Frame.title", "java.lang.String", "matterhorn-secret – Foo.kt"),
                                              ReferenceStep(" (root)", ObjectRendering("com.example.Holder", "x"), "Holder of matterhorn-secret")),
                                       listOf(ObjectRendering("com.intellij.openapi.project.impl.ProjectImpl",
                                                              "Project(name=matterhorn-secret, containerState=DISPOSE_COMPLETED)")))

  @Test
  fun testMaskedReportHasOnlyTypes() {
    val report = LeakDetectionRunner.getInstance().reporter().buildIdleReport(listOf(leakedProject))

    assertFalse(report.contains("matterhorn-secret"), report)
    assertTrue(report.contains("  reference path:\n" +
                               "    via 'com.example.Frame.title' of class java.lang.String\n" +
                               "    via ' (root)' of class com.example.Holder\n"), report)
    assertTrue(report.contains("  Disposer parent chain:\n" +
                               "    com.intellij.openapi.project.impl.ProjectImpl\n" +
                               "      <- ROOT"), report)
  }

  @Test
  fun testIdleReportHasOnlyLeakEntries() {
    val report = LeakDetectionRunner.getInstance().reporter().buildIdleReport(listOf(leakedProject))

    assertTrue(report.startsWith("Project leak detection found 1 leaked instance(s)"), report)
    assertFalse(report.contains("# IDE:"), report)
    assertFalse(report.contains("```"), report)
    assertFalse(report.contains("matterhorn-secret"), report)
  }

  @Test
  fun testUnmaskedReportHasValues() {
    val report = LeakDetectionRunner.getInstance().reporter().buildInteractiveReport(listOf(leakedProject))

    assertTrue(report.contains("  instance: Project(name=matterhorn-secret, containerState=DISPOSE_COMPLETED)"), report)
    assertTrue(report.contains("    via 'com.example.Frame.title'; Value: 'matterhorn-secret – Foo.kt' of class java.lang.String\n" +
                               "    via ' (root)'; Value: 'x' of class com.example.Holder\n" +
                               "     (from Holder of matterhorn-secret)"), report)
    assertTrue(report.contains("    Project(name=matterhorn-secret, containerState=DISPOSE_COMPLETED) (com.intellij.openapi.project.impl.ProjectImpl)"),
               report)
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
  fun testRootsUseFocusManagerNotWeakWindowSnapshots() {
    // Window.getWindows()/Frame.getFrames() strongify AWT's weak window lists and report false leaks (IJPL-249998),
    // so they must not be roots; the KeyboardFocusManager (a real object) still is.
    val labels = ProjectLeakDetector().buildRoots().values
    assertTrue("KeyboardFocusManager.getCurrentKeyboardFocusManager()" in labels,
               "expected a KeyboardFocusManager root, got: $labels")
    assertFalse("java.awt.Window.getWindows()" in labels,
                "weakly-held AWT windows must not be treated as a root, got: $labels")
    assertFalse("java.awt.Frame.getFrames()" in labels,
                "weakly-held AWT frames must not be treated as a root, got: $labels")
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

  private fun step(field: String, className: String, text: String = "x"): ReferenceStep =
    ReferenceStep(field, ObjectRendering(className, text))

  companion object {
    @JvmStatic
    @Suppress("unused")
    var leakedEditor: Editor? = null
  }
}
