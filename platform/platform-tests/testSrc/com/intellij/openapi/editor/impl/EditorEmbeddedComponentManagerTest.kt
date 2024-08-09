// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.*
import com.intellij.util.DocumentUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.*
import org.junit.rules.TestName
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.awt.Dimension
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class EditorEmbeddedComponentManagerTest {
  private val fontWidth = 10
  private val fontHeight = 10
  private val testName = TestName()
  private val disposableRule = DisposableRule()
  private val temporaryDirectory = TemporaryDirectory()
  private lateinit var editor: EditorEx

  private val documentPrinter = object : TestWatcher() {
    override fun failed(e: Throwable, description: Description) {
      if (this@EditorEmbeddedComponentManagerTest::editor.isInitialized) {
        e.addSuppressed(Throwable(editorDebugRepresentation()))
      }
    }
  }

  @JvmField
  @Rule
  val ruleChain = RuleChain(testName, temporaryDirectory, disposableRule, documentPrinter)

  @Before
  fun before() {
    EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
      override fun editorCreated(event: EditorFactoryEvent) {
        event.editor.colorsScheme.lineSpacing = 1.0f  // To simplify calculations.
      }
    }, disposableRule.disposable)

    val virtualFile = temporaryDirectory.createVirtualFile("${this::class.java.simpleName}.${testName.methodName}.txt", """
      The first line.
      The second line.
      The third line.
      The fourth line.
      The fifth line.
    """.trimIndent())

    val fileEditorManager = FileEditorManager.getInstance(projectRule.project)
    Disposer.register(disposableRule.disposable, Disposable {
      FontLayoutService.setInstance(null)
      invokeAndWaitIfNeeded {
        editor.component.removeNotify()
        FileDocumentManager.getInstance().saveDocument(editor.document)
        fileEditorManager.closeFile(virtualFile)
      }
    })
    FontLayoutService.setInstance(MockFontLayoutService(fontWidth, fontHeight, 0))
    invokeAndWaitIfNeeded {
      editor = fileEditorManager.openTextEditor(OpenFileDescriptor(projectRule.project, virtualFile), true) as EditorEx
      editor.component.addNotify()
      editor.component.size = Dimension(1000, 500)
      editor.component.validate()
    }
  }

  @Test
  fun `add one component`() = edt {
    add(2, JPanel().apply { preferredSize = Dimension(37, 37) })

    pollAssertions {
      assertYShift(1, 0, "The line above the component")
      assertYShift(2, 37, "The line below the component")
    }
  }

  @Test
  fun `add component and another component below`() = edt {
    add(4, JPanel().apply { preferredSize = Dimension(43, 43) })
    add(2, JPanel().apply { preferredSize = Dimension(37, 37) })

    pollAssertions {
      assertYShift(1, 0, "The line above the first component")
      assertYShift(2, 37, "The line below the first component")
      assertYShift(3, 37, "The line above the second component")
      assertYShift(4, 37 + 43, "The line below the second component")
    }
  }

  @Test
  fun `add component and another component above`() = edt {
    add(2, JPanel().apply { preferredSize = Dimension(37, 37) })
    add(4, JPanel().apply { preferredSize = Dimension(43, 43) })

    pollAssertions {
      assertYShift(1, 0, "The line above the first component")
      assertYShift(2, 37, "The line below the first component")
      assertYShift(3, 37, "The line above the second component")
      assertYShift(4, 37 + 43, "The line below the second component")
    }
  }

  @Test
  fun `add two separate components and then insert line between`() = edt {
    writeIntentReadAction {
      add(2, JPanel().apply { preferredSize = Dimension(37, 37) })
      add(4, JPanel().apply { preferredSize = Dimension(43, 43) })

      WriteCommandAction.runWriteCommandAction(projectRule.project) {
        editor.document.insertString(editor.document.getLineStartOffset(3), "A new line between components.\n")
      }
    }

    pollAssertions {
      assertYShift(1, 0, "The line above the first component")
      assertYShift(2, 37, "The line below the first component")
      assertYShift(4, 37, "The line above the second component")
      assertYShift(5, 37 + 43, "The line below the second component")
    }
  }

  @Test
  fun `add two separate components and resize the upper one`() = edt {
    val component = JPanel().apply { preferredSize = Dimension(37, 37) }
    add(2, component)
    add(4, JPanel().apply { preferredSize = Dimension(43, 43) })

    component.preferredSize = Dimension(19, 19)
    component.invalidate()

    pollAssertions {
      assertYShift(1, 0, "The line above the first component")
      assertYShift(2, 19, "The line below the first component")
      assertYShift(3, 19, "The line above the second component")
      assertYShift(4, 19 + 43, "The line below the second component")
    }
  }

  @Test
  fun `add two separate components and resize the lower one`() = edt {
    add(2, JPanel().apply { preferredSize = Dimension(37, 37) })
    val component = JPanel().apply { preferredSize = Dimension(43, 43) }
    add(4, component)

    component.preferredSize = Dimension(19, 19)
    component.invalidate()

    pollAssertions {
      assertYShift(1, 0, "The line above the first component")
      assertYShift(2, 37, "The line below the first component")
      assertYShift(3, 37, "The line above the second component")
      assertYShift(4, 37 + 19, "The line below the second component")
    }
  }

  @Test
  fun `add two separate components and remove the upper one`() = edt {
    val inlay = add(2, JPanel().apply { preferredSize = Dimension(37, 37) })
    add(4, JPanel().apply { preferredSize = Dimension(43, 43) })

    Disposer.dispose(inlay)

    pollAssertions {
      assertYShift(1, 0, "The line above the first component")
      assertYShift(2, 0, "The line below the first component")
      assertYShift(3, 0, "The line above the second component")
      assertYShift(4, 43, "The line below the second component")
    }
  }

  @Test
  fun `add two separate components and remove the lower one`() = edt {
    add(2, JPanel().apply { preferredSize = Dimension(37, 37) })
    val inlay = add(4, JPanel().apply { preferredSize = Dimension(43, 43) })

    Disposer.dispose(inlay)

    pollAssertions {
      assertYShift(1, 0, "The line above the first component")
      assertYShift(2, 37, "The line below the first component")
      assertYShift(3, 37, "The line above the second component")
      assertYShift(4, 37, "The line below the second component")
    }
  }

  @Test
  fun `viewport resize`() = edt {
    if (SystemInfo.isMac && UsefulTestCase.IS_UNDER_TEAMCITY) {
      throw AssumptionViolatedException("For unclear reason, this test doesn't work on macOS on CI")
    }
    val panel = JPanel()
    panel.preferredSize = Dimension(500, 10)
    add(2, panel)

    pollAssertions {
      assertThat(panel.width).isEqualTo(500)
    }

    // What consumes additional 2 pixels?
    val excessiveWidth = editor.gutterComponentEx.width + editor.scrollPane.verticalScrollBar.width + 2

    editor.component.size = Dimension(499 + excessiveWidth, 100)
    pollAssertions {
      assertThat(panel.width).isEqualTo(499)
    }

    editor.component.size = Dimension(100 + excessiveWidth, 100)
    pollAssertions {
      assertThat(panel.width).isEqualTo(100)
    }

    editor.component.size = Dimension(1000, 100)
    pollAssertions {
      assertThat(panel.width).isEqualTo(500)
    }
  }

  @Test
  fun `bulk mode`() = edt {
    writeIntentReadAction {
      DocumentUtil.executeInBulk(editor.document) {
        add(4, JPanel().apply { preferredSize = Dimension(19, 19) })
        WriteCommandAction.runWriteCommandAction(projectRule.project) {
          editor.document.insertString(editor.document.getLineStartOffset(3), "A new line.\n")
        }
        add(2, JPanel().apply { preferredSize = Dimension(13, 13) })
      }
    }

    pollAssertions {
      assertYShift(1, 0, "The line above the first component")
      assertYShift(2, 13, "The line below the first component")
      assertYShift(4, 13, "The line above the second component")
      assertYShift(5, 13 + 19, "The line below the second component")
    }
  }

  private fun add(line: Int, component: JPanel): Inlay<*> =
    EditorEmbeddedComponentManager.getInstance().addComponent(editor, component, EditorEmbeddedComponentManager.Properties(
      EditorEmbeddedComponentManager.ResizePolicy.none(),
      null,
      false,
      true,
      0,
      editor.logicalPositionToOffset(LogicalPosition(line, 0))
    ))!!

  private fun assertYShift(line: Int, yShift: Int, description: String) {
    assertThat(editor.logicalPositionToXY(LogicalPosition(line, 0)).y)
      .describedAs(description)
      .isEqualTo(fontHeight * line + yShift)
  }

  private fun editorDebugRepresentation(): String = invokeAndWaitIfNeeded {
    val text = StringBuilder()
    var offset = 0
    for ((lineNumber, line) in editor.document.text.lineSequence().withIndex()) {
      val yRange = editor.logicalPositionToXY(LogicalPosition(lineNumber, 0)).y.let { "$it..${it + fontHeight - 1}" }
      val inlays = editor.inlayModel.getBlockElementsInRange(offset, offset + line.length)

      val (inlaysAbove, inlaysBelow) = inlays
        .map {
          val c = it.renderer as JComponent
          val p = SwingUtilities.convertPoint(c, Point(0, 0), editor.contentComponent)
          it to "X [${p.x}, +${c.width}=${p.x + c.width}) Y [${p.y}, ${p.y}+${c.height}=${p.y + c.height})"
        }
        .partition { it.first.placement == Inlay.Placement.ABOVE_LINE }
      for ((_, bounds) in inlaysAbove) {
        text.append("\nComponent above line $lineNumber, offset $offset: $bounds")
      }

      text.append("\nLine ${lineNumber.toString().padEnd(2)} Offset ${offset.toString().padEnd(3)} Y ${yRange.padEnd(8)}: $line")

      for ((_, bounds) in inlaysBelow) {
        text.append("\nComponent below line $lineNumber, offset $offset: $bounds")
      }

      offset += line.length + 1
    }
    text.toString()
  }

  private fun edt(handler: suspend CoroutineScope.() -> Unit): Unit =
    runBlocking(Dispatchers.EDT + ModalityState.defaultModalityState().asContextElement(), handler)

  /** Used for awaiting for RepaintManager validating all invalid components. */
  private suspend inline fun pollAssertions(crossinline handler: () -> Unit) {
    pollAssertionsAsync(5.seconds, 50.milliseconds) {
      editor.component.validate()
      handler()
    }
  }

  companion object {
    private val projectRule = ProjectRule()

    @ClassRule
    @JvmField
    val classRuleChain = RuleChain(ApplicationRule(), projectRule)
  }
}