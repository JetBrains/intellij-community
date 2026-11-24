// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.popup.PopupDispatcher
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

@OptIn(ExperimentalCoroutinesApi::class)
@TestApplication
internal class CodeFloatingToolbarTest {
  @Test
  fun `hovering over popup group button without clicking does not steal typing from editor`(): Unit = runBlocking {
    withToolbarFixture { editor, toolbar, scheduler ->
      val extractButton =
        withContext(Dispatchers.EDT) {
          findButtons(toolbar).first { it.presentation.text == "Extract" }
        }

      dispatchHover(extractButton, MouseEvent.MOUSE_ENTERED)

      // Advance virtual time well past the 300ms hover delay without clicking the button.
      advanceHoverTime(scheduler, 500)

      withContext(Dispatchers.EDT) {
        assertThat(IdeEventQueue.getInstance().isPopupActive)
          .describedAs("Hovering over a dropdown button without clicking must not open an active popup")
          .isFalse()
      }

      // Type 'x' to replace the selected text in the editor.
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          val target = editor.contentComponent
          val now = System.currentTimeMillis()
          val events =
            listOf(
              KeyEvent(target, KeyEvent.KEY_PRESSED, now, 0, KeyEvent.VK_X, 'x'),
              KeyEvent(target, KeyEvent.KEY_TYPED, now, 0, KeyEvent.VK_UNDEFINED, 'x'),
              KeyEvent(target, KeyEvent.KEY_RELEASED, now, 0, KeyEvent.VK_X, 'x'),
            )
          val queue = IdeEventQueue.getInstance()
          val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
          for (keyEvent in events) {
            if (!queue.isPopupActive || !queue.popupManager.dispatch(keyEvent)) {
              focusManager.redispatchEvent(target, keyEvent)
            }
          }
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        }
      }

      withContext(Dispatchers.EDT) {
        assertThat(editor.document.text)
          .describedAs("Typing after mouse selection should replace selected editor text rather than entering popup speed search")
          .isEqualTo("x world")
      }
    }
  }

  @Test
  fun `hovering over sibling popup group button switches menu after 40ms only while a popup remains open`(): Unit = runBlocking {
    val extractInvocations = AtomicInteger(0)
    val surroundInvocations = AtomicInteger(0)
    val trackedExtractGroup = createTrackedDropdownGroup("TrackedExtract", extractInvocations)
    val surroundGroup = createTrackedDropdownGroup("Surround", surroundInvocations)

    withToolbarFixture(extraActions = listOf(trackedExtractGroup, surroundGroup)) { _, toolbar, scheduler ->
      val (extractButton, surroundButton) =
        withContext(Dispatchers.EDT) {
          val buttons = findButtons(toolbar)
          buttons.first { it.presentation.text == "TrackedExtract" } to buttons.first { it.presentation.text == "Surround" }
        }

      val openPopup = FakePopup()
      withContext(Dispatchers.EDT) {
        toolbar.attachPopupToButton(extractButton, openPopup.popup)
        openPopup.fireBeforeShown()
      }

      // Hovering over the already-selected button should not re-click or cancel its popup.
      dispatchHover(extractButton, MouseEvent.MOUSE_ENTERED)
      advanceHoverTime(scheduler, 500)
      assertThat(extractInvocations.get()).isEqualTo(0)
      assertThat(openPopup.cancelled.get()).isFalse()

      // Hovering over a sibling popup-group button triggers exactly at the 40ms threshold, not before.
      dispatchHover(extractButton, MouseEvent.MOUSE_EXITED)
      dispatchHover(surroundButton, MouseEvent.MOUSE_ENTERED)
      advanceHoverTime(scheduler, 39)
      assertThat(surroundInvocations.get()).isEqualTo(0)
      advanceHoverTime(scheduler, 1)
      assertThat(surroundInvocations.get()).isEqualTo(1)

      // If the active popup closes during the 40ms hover delay, the pending hover must not open a new popup.
      dispatchHover(surroundButton, MouseEvent.MOUSE_EXITED)
      dispatchHover(surroundButton, MouseEvent.MOUSE_ENTERED)
      advanceHoverTime(scheduler, 20)
      withContext(Dispatchers.EDT) {
        openPopup.popup.cancel()
      }
      advanceHoverTime(scheduler, 50)
      assertThat(surroundInvocations.get()).isEqualTo(1)
    }
  }

  @Test
  fun `hovering over non-popup button cancels active popup at 300ms threshold unless exited early`(): Unit = runBlocking {
    val regularAction =
      object : AnAction("Comment") {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun actionPerformed(e: AnActionEvent) {}
      }

    withToolbarFixture(extraActions = listOf(regularAction)) { _, toolbar, scheduler ->
      val (extractButton, commentButton) =
        withContext(Dispatchers.EDT) {
          val buttons = findButtons(toolbar)
          buttons.first { it.presentation.text == "Extract" } to buttons.first { it.presentation.text == "Comment" }
        }

      val openPopup = FakePopup()
      withContext(Dispatchers.EDT) {
        toolbar.attachPopupToButton(extractButton, openPopup.popup)
        openPopup.fireBeforeShown()
      }

      // Exiting a non-popup button at 299ms must cancel the pending hover job without closing the popup.
      dispatchHover(commentButton, MouseEvent.MOUSE_ENTERED)
      advanceHoverTime(scheduler, 299)
      dispatchHover(commentButton, MouseEvent.MOUSE_EXITED)
      advanceHoverTime(scheduler, 300)
      assertThat(openPopup.cancelled.get()).isFalse()

      // Staying hovered over a non-popup button cancels the active popup at the 300ms threshold, not at 299ms.
      dispatchHover(commentButton, MouseEvent.MOUSE_ENTERED)
      advanceHoverTime(scheduler, 299)
      assertThat(openPopup.cancelled.get()).isFalse()
      advanceHoverTime(scheduler, 1)
      assertThat(openPopup.cancelled.get()).isTrue()
    }
  }

  private fun createTrackedDropdownGroup(title: String, invocations: AtomicInteger): DefaultActionGroup {
    return object : DefaultActionGroup(title, true), CustomComponentAction {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
          return object : ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
            override fun actionPerformed(event: AnActionEvent) {
              invocations.incrementAndGet()
            }
          }
        }
      }
      .apply {
        add(
          object : AnAction("$title Child") {
            override fun actionPerformed(e: AnActionEvent) {}
          }
        )
      }
  }

  private suspend fun withToolbarFixture(
    extraActions: List<AnAction> = emptyList(),
    block: suspend (Editor, CodeFloatingToolbar, TestCoroutineScheduler) -> Unit,
  ) {
    val testDisposable = Disposer.newDisposable("CodeFloatingToolbarTest")
    val scheduler = TestCoroutineScheduler()
    val parentScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
    try {
      val actionGroupId = "Test.Floating.CodeToolbar"
      val extractGroup =
        RefactorDropdownActionGroup().apply {
          isPopup = true
          templatePresentation.text = "Extract"
          add(
            object : AnAction("Extract Method") {
              override fun actionPerformed(e: AnActionEvent) {}
            }
          )
        }
      val toolbarGroup =
        DefaultActionGroup().apply {
          add(extractGroup)
          extraActions.forEach(::add)
        }
      val actionManager = ActionManager.getInstance()
      actionManager.registerAction(actionGroupId, toolbarGroup)
      Disposer.register(testDisposable) { actionManager.unregisterAction(actionGroupId) }

      val bean =
        FloatingToolbarLanguageBean().apply {
          language = FileTypes.PLAIN_TEXT.language.id
          actionGroup = actionGroupId
          hideIntentionsGroup = true
          hideConfigurationsGroup = true
          setPluginDescriptor(DefaultPluginDescriptor(PluginId.getId("com.intellij"), CodeFloatingToolbarTest::class.java.classLoader))
        }
      val epName = ExtensionPointName.create<FloatingToolbarLanguageBean>("com.intellij.lang.floatingToolbar")
      ExtensionTestUtil.maskExtensions(epName, listOf(bean), testDisposable)

      val project = ProjectManager.getInstance().defaultProject
      val editor =
        withContext(Dispatchers.EDT) {
          writeIntentReadAction {
            val psiFile = PsiFileFactory.getInstance(project).createFileFromText("sample.txt", FileTypes.PLAIN_TEXT, "hello world")
            val document = checkNotNull(psiFile.viewProvider.document)
            EditorFactory.getInstance().createEditor(document, project, FileTypes.PLAIN_TEXT, false).also { ed ->
              Disposer.register(testDisposable) { EditorFactory.getInstance().releaseEditor(ed) }
              ed.selectionModel.setSelection(0, 5)
              ed.caretModel.moveToOffset(5)
            }
          }
        }

      val toolbarScope = parentScope.childScope("CodeFloatingToolbar")
      val shown = AtomicBoolean(false)
      val toolbar =
        withContext(Dispatchers.EDT) {
          writeIntentReadAction {
            CodeFloatingToolbar(editor, toolbarScope).also { tb ->
              Disposer.register(testDisposable, tb)
              tb.show { shown.set(true) }
            }
          }
        }

      waitUntil(scheduler) { shown.get() && toolbar.hintComponent != null }

      block(editor, toolbar, scheduler)
    } finally {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          PopupDispatcher.getInstance().close()
          Disposer.dispose(testDisposable)
        }
      }
      parentScope.cancel()
    }
  }

  private fun findButtons(toolbar: CodeFloatingToolbar): List<ActionButton> {
    val hintComponent = checkNotNull(toolbar.hintComponent) { "Expected CodeFloatingToolbar hintComponent to be created" }
    return UIUtil.findComponentsOfType(hintComponent, ActionButton::class.java)
  }

  private suspend fun dispatchHover(button: ActionButton, eventId: Int) {
    withContext(Dispatchers.EDT) {
      button.dispatchEvent(MouseEvent(button, eventId, System.currentTimeMillis(), 0, 5, 5, 0, false))
    }
  }

  private suspend fun advanceHoverTime(scheduler: TestCoroutineScheduler, ms: Long) {
    scheduler.advanceTimeBy(ms)
    scheduler.runCurrent()
    flushEdt()
    scheduler.runCurrent()
  }

  private suspend fun waitUntil(scheduler: TestCoroutineScheduler, predicate: () -> Boolean) {
    for (attempt in 0 until 200) {
      scheduler.runCurrent()
      flushEdt()
      scheduler.runCurrent()
      if (predicate()) return
      delay(10)
    }
    fail("Condition not met within timeout")
  }

  private suspend fun flushEdt() {
    withContext(Dispatchers.EDT) { PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() }
  }

  private class FakePopup {
    private val listeners = CopyOnWriteArrayList<JBPopupListener>()
    private val content = JPanel()
    val cancelled = AtomicBoolean(false)

    val popup: JBPopup =
      Proxy.newProxyInstance(JBPopup::class.java.classLoader, arrayOf(JBPopup::class.java)) { proxy, method, args ->
        when (method.name) {
          "equals" -> proxy === args?.get(0)
          "hashCode" -> System.identityHashCode(proxy)
          "toString" -> "FakeJBPopup"
          "addListener" -> {
            listeners.add(args[0] as JBPopupListener)
            Unit
          }
          "getContent" -> content
          "getSize" -> Dimension(100, 100)
          "isDisposed" -> cancelled.get()
          "isVisible" -> !cancelled.get()
          "cancel",
          "dispose" -> {
            if (cancelled.compareAndSet(false, true)) {
              val event = LightweightWindowEvent(proxy as JBPopup, false)
              listeners.forEach { it.onClosed(event) }
            }
            Unit
          }
          else ->
            when (method.returnType) {
              Boolean::class.java -> false
              Int::class.java -> 0
              else -> null
            }
        }
      } as JBPopup

    fun fireBeforeShown() {
      val event = LightweightWindowEvent(popup)
      listeners.forEach { it.beforeShown(event) }
    }
  }
}
