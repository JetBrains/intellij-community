// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.audioCues.AudioCue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointProxy
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.lang.reflect.Proxy

@TestApplication
@Timeout(30)
class BreakpointLineAudioCueDetectorTest {
  private val detector = BreakpointLineAudioCueDetector()

  @TestDisposable
  lateinit var testDisposable: Disposable

  @Test
  fun `the debugger provider publishes a valid cue`() {
    val cues = DebuggerAudioCueProvider().audioCues

    assertEquals(listOf("breakpoint.line"), cues.map { it.id })
    for (cue in cues) {
      assertTrue(cue.title.isNotBlank())
      val sound = checkNotNull(cue.ownerClass.classLoader.getResourceAsStream(cue.resourcePath)) { "No sound for '${cue.id}'" }
      assertTrue(sound.use { it.read() } != -1)
    }
  }

  @Test
  fun `a breakpoint on the line is reported`() = withEditor { editor ->
    installBreakpoints(1)

    assertEquals(setOf(DebuggerAudioCues.BREAKPOINT_LINE), detect(editor, 1, LINE_1_START))
  }

  @Test
  fun `only the breakpoint's own line is reported`() = withEditor { editor ->
    installBreakpoints(2)

    assertEquals(emptySet<AudioCue>(), detect(editor, 1, LINE_1_START))
    assertEquals(setOf(DebuggerAudioCues.BREAKPOINT_LINE), detect(editor, 2, LINE_2_START))
    assertEquals(emptySet<AudioCue>(), detect(editor, 3, LINE_3_START))
  }

  @Test
  fun `a line without breakpoints is not reported`() = withEditor { editor ->
    installBreakpoints()

    assertEquals(emptySet<AudioCue>(), detect(editor, 1, LINE_1_START))
  }

  @Test
  fun `breakpoints are not reported in a diff editor`() = withEditor(EditorKind.DIFF) { editor ->
    installBreakpoints(1)

    assertEquals(emptySet<AudioCue>(), detect(editor, 1, LINE_1_START))
  }

  @Test
  fun `breakpoints are not reported in an editor without a project`() = withEditor(project = null) { editor ->
    installBreakpoints(1)

    assertEquals(emptySet<AudioCue>(), detect(editor, 1, LINE_1_START))
  }

  private fun installBreakpoints(vararg lines: Int) {
    val breakpoints = lines.map { line ->
      proxy<XLineBreakpointProxy> { methodName ->
        when (methodName) {
          "getLine" -> line
          else -> error("Unexpected XLineBreakpointProxy call: $methodName")
        }
      }
    }
    val lineBreakpointManager = proxy<XLineBreakpointManagerProxy> { methodName ->
      when (methodName) {
        "getDocumentBreakpointProxies" -> breakpoints
        else -> error("Unexpected XLineBreakpointManagerProxy call: $methodName")
      }
    }
    val breakpointManager = proxy<XBreakpointManagerProxy> { methodName ->
      when (methodName) {
        "getLineBreakpointManager" -> lineBreakpointManager
        else -> error("Unexpected XBreakpointManagerProxy call: $methodName")
      }
    }
    val manager = proxy<XDebugManagerProxy> { methodName ->
      when (methodName) {
        "isEnabled" -> true
        "getBreakpointManagerProxy" -> breakpointManager
        else -> error("Unexpected XDebugManagerProxy call: $methodName")
      }
    }
    ExtensionTestUtil.maskExtensions(X_DEBUG_MANAGER_PROXY_EP, listOf(manager), testDisposable)
  }

  private fun detect(editor: Editor, line: Int, caretOffset: Int): Set<AudioCue> =
    detector.detect(editor, line, caretOffset).mapTo(HashSet()) { it.cue }

  private fun withEditor(
    kind: EditorKind = EditorKind.MAIN_EDITOR,
    project: Project? = projectFixture.get(),
    body: (Editor) -> Unit,
  ) = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val factory = EditorFactory.getInstance()
        val editor = factory.createEditor(factory.createDocument(TEXT), project, kind)
        try {
          body(editor)
        }
        finally {
          factory.releaseEditor(editor)
        }
      }
    }
  }

  private companion object {
    val projectFixture = projectFixture()

    const val TEXT: String = "line0\nline1\nline2\nline3"
    const val LINE_1_START: Int = 6
    const val LINE_2_START: Int = 12
    const val LINE_3_START: Int = 18
  }
}

private val X_DEBUG_MANAGER_PROXY_EP = ExtensionPointName.create<XDebugManagerProxy>("com.intellij.xdebugger.managerProxy")

private inline fun <reified T> proxy(crossinline methodResult: (String) -> Any?): T {
  return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { instance, method, arguments ->
    when (method.name) {
      "equals" -> instance === arguments?.firstOrNull()
      "hashCode" -> System.identityHashCode(instance)
      "toString" -> "Test proxy for ${T::class.java.name}"
      else -> methodResult(method.name)
    }
  } as T
}
