// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate.quick.common

import com.intellij.concurrency.ResultConsumer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.EditorFactory
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.ui.AppUIUtil
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.xdebugger.frame.ImmediateFullValueEvaluator
import com.intellij.xdebugger.frame.XFullValueEvaluator
import com.intellij.xdebugger.frame.XFullValueEvaluator.XFullValueEvaluationCallback
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.impl.ui.XValueTextProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.awt.Point
import java.util.concurrent.TimeUnit

class XDebuggerTextPopupTest {
  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun `loading text is replaced by the preview and full value`() = timeoutRunBlocking {
    withEvaluation(LOADING_TEXT) { value, callback ->
      withContext(Dispatchers.EDT) {
        value.present()
        value.present(PREVIEW)
        value.node.setFullValueEvaluator(ImmediateFullValueEvaluator(FULL_TEXT))
        value.present()
      }
      assertEquals(listOf(LOADING_TEXT, PREVIEW, FULL_TEXT), callback.values)
    }
  }

  @Test
  fun `preview cannot replace an earlier full value`() = timeoutRunBlocking {
    withEvaluation { value, callback ->
      withContext(Dispatchers.EDT) {
        value.node.setFullValueEvaluator(ImmediateFullValueEvaluator(FULL_TEXT))
        value.present()
      }
      assertEquals(listOf(FULL_TEXT), callback.values)
    }
  }

  @Test
  fun `loading text is replaced without a full value evaluator`() = timeoutRunBlocking {
    withEvaluation(LOADING_TEXT) { value, callback ->
      withContext(Dispatchers.EDT) {
        value.present()
        value.present("short text")
      }
      assertEquals(listOf(LOADING_TEXT, "short text"), callback.values)
    }
  }

  @Test
  fun `obsolete popup ignores presentations and evaluators`() = timeoutRunBlocking {
    withEvaluation { value, callback ->
      withContext(Dispatchers.EDT) {
        assertFalse(value.node.isObsolete)
        callback.obsolete = true
        assertTrue(value.node.isObsolete)
        value.present()
        value.node.setFullValueEvaluator(object : XFullValueEvaluator() {
          override fun startEvaluation(callback: XFullValueEvaluationCallback) {
            error("The evaluator must not start for an obsolete popup")
          }
        })
      }
      assertTrue(callback.values.isEmpty())
    }
  }

  @Test
  fun `evaluator received in background starts on EDT`() = timeoutRunBlocking {
    withEvaluation { value, callback ->
      val completed = CompletableDeferred<Boolean>()
      withContext(Dispatchers.Default) {
        value.node.setFullValueEvaluator(object : XFullValueEvaluator() {
          override fun startEvaluation(fullCallback: XFullValueEvaluationCallback) {
            fullCallback.evaluated(FULL_TEXT)
            completed.complete(ApplicationManager.getApplication().isDispatchThread)
          }
        })
      }
      assertTrue(completed.await())
      assertEquals(listOf(FULL_TEXT), callback.values)
    }
  }

  @Test
  fun `full value errors reach the popup callback`() = timeoutRunBlocking {
    withEvaluation { value, callback ->
      withContext(Dispatchers.EDT) {
        value.node.setFullValueEvaluator(object : XFullValueEvaluator() {
          override fun startEvaluation(callback: XFullValueEvaluationCallback) {
            callback.errorOccurred("evaluation failed")
          }
        })
        value.present()
      }
      assertEquals(listOf("evaluation failed"), callback.errors)
      assertTrue(callback.values.isEmpty())
    }
  }

  @Test
  fun `superseded evaluation cannot replace a newer result`() = timeoutRunBlocking {
    withEvaluation { value, callback ->
      val evaluator = DeferredEvaluator()
      val oldCallback = withContext(Dispatchers.EDT) {
        value.node.setFullValueEvaluator(evaluator)
        evaluator.callback
      }
      withContext(Dispatchers.EDT) {
        value.node.setFullValueEvaluator(evaluator)
      }
      withContext(Dispatchers.Default) {
        assertTrue(oldCallback.isObsolete)
        assertFalse(evaluator.callback.isObsolete)
        evaluator.callback.evaluated(FULL_TEXT)
        oldCallback.evaluated("stale text")
        oldCallback.errorOccurred("stale error")
        evaluator.callback.evaluated("updated full text")
      }
      flushEdtCallbacks()
      assertEquals(listOf(FULL_TEXT, "updated full text"), callback.values)
      assertTrue(callback.errors.isEmpty())
    }
  }

  @Test
  fun `queued results are ignored when evaluation is superseded`() = timeoutRunBlocking {
    withEvaluation { value, callback ->
      val evaluator = DeferredEvaluator()
      withContext(Dispatchers.EDT) {
        value.node.setFullValueEvaluator(evaluator)
        AppExecutorUtil.getAppExecutorService().submit {
          evaluator.callback.evaluated("stale text")
          evaluator.callback.errorOccurred("stale error")
        }.get(5, TimeUnit.SECONDS)
        value.node.setFullValueEvaluator(ImmediateFullValueEvaluator(FULL_TEXT))
      }
      flushEdtCallbacks()
      assertEquals(listOf(FULL_TEXT), callback.values)
      assertTrue(callback.errors.isEmpty())
    }
  }

  @Test
  fun `obsolete popup ignores results of a running evaluator`() = timeoutRunBlocking {
    withEvaluation { value, callback ->
      val evaluator = DeferredEvaluator()
      withContext(Dispatchers.EDT) {
        value.node.setFullValueEvaluator(evaluator)
        callback.obsolete = true
      }
      withContext(Dispatchers.Default) {
        assertTrue(evaluator.callback.isObsolete)
        evaluator.callback.evaluated(FULL_TEXT)
        evaluator.callback.errorOccurred("late error")
      }
      flushEdtCallbacks()
      assertTrue(callback.values.isEmpty())
      assertTrue(callback.errors.isEmpty())
    }
  }

  @Test
  fun `background results and errors reach the popup on EDT`() = timeoutRunBlocking {
    withEvaluation { value, callback ->
      val evaluator = DeferredEvaluator()
      withContext(Dispatchers.EDT) {
        value.node.setFullValueEvaluator(evaluator)
      }
      withContext(Dispatchers.Default) {
        evaluator.callback.evaluated(FULL_TEXT)
        evaluator.callback.errorOccurred("evaluation failed")
      }
      flushEdtCallbacks()
      assertEquals(listOf(FULL_TEXT), callback.values)
      assertEquals(listOf("evaluation failed"), callback.errors)
    }
  }

  private suspend fun flushEdtCallbacks() {
    val completed = CompletableDeferred<Unit>()
    AppUIUtil.invokeOnEdt { completed.complete(Unit) }
    completed.await()
  }

  private suspend fun withEvaluation(
    text: String = PREVIEW,
    action: suspend (TextValue, RecordingCallback) -> Unit,
  ) {
    val project = projectRule.project
    val editorFactory = EditorFactory.getInstance()
    val editor = withContext(Dispatchers.EDT) {
      editorFactory.createEditor(editorFactory.createDocument(""), project)
    }
    try {
      val value = TextValue(text)
      val callback = RecordingCallback()
      withContext(Dispatchers.EDT) {
        val popup = object : XDebuggerTextPopup<Unit>(null, value, UnusedTreeCreator, Unit, editor, Point(), project, null) {
          fun startEvaluation() {
            checkNotNull(myEvaluator).startEvaluation(callback)
          }
        }
        popup.startEvaluation()
      }
      action(value, callback)
    }
    finally {
      withContext(Dispatchers.EDT) {
        editorFactory.releaseEditor(editor)
      }
    }
  }

  private class TextValue(private var text: String) : XValue(), XValueTextProvider {
    lateinit var node: XValueNode

    override fun computePresentation(node: XValueNode, place: XValuePlace) {
      assertEquals(XValuePlace.TOOLTIP, place)
      this.node = node
    }

    override fun getValueText(): String = text

    override fun shouldShowTextValue(): Boolean = true

    fun present(text: String = this.text) {
      this.text = text
      node.setPresentation(null, null, text, true)
    }
  }

  private class DeferredEvaluator : XFullValueEvaluator() {
    lateinit var callback: XFullValueEvaluationCallback

    override fun startEvaluation(callback: XFullValueEvaluationCallback) {
      this.callback = callback
    }
  }

  private class RecordingCallback : XFullValueEvaluationCallback {
    val values = mutableListOf<String>()
    val errors = mutableListOf<String>()
    var obsolete = false

    override fun evaluated(fullValue: String) {
      assertTrue(ApplicationManager.getApplication().isDispatchThread)
      values.add(fullValue)
    }

    override fun errorOccurred(errorMessage: String) {
      assertTrue(ApplicationManager.getApplication().isDispatchThread)
      errors.add(errorMessage)
    }

    override fun isObsolete(): Boolean = obsolete
  }

  private object UnusedTreeCreator : DebuggerTreeCreator<Unit> {
    override fun getTitle(descriptor: Unit): String = error("The tree must not be created")

    @Suppress("DEPRECATION")
    override fun createDescriptorByNode(node: Any?, resultConsumer: ResultConsumer<in Unit>) {
      error("The tree must not be created")
    }

    override fun createTree(descriptor: Unit): Tree = error("The tree must not be created")
  }

  companion object {
    private const val LOADING_TEXT = "Collecting data..."
    private val FULL_TEXT = ".".repeat(1000) + "X".repeat(1000)
    private val PREVIEW = FULL_TEXT.take(1005)
  }
}
