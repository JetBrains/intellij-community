// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.applyProjectFrameUiPolicy
import com.intellij.openapi.project.impl.getOrCreateFrameContentPaintedDeferred
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.openapi.wm.impl.ToolWindowManagerState
import com.intellij.openapi.wm.impl.ToolWindowManagerStateImpl
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

private const val EAGER_ID = "EagerTestToolWindow"
private const val DEFERRED_ID = "DeferredTestToolWindow"

/**
 * The deferred tool window registration of a welcome-experience frame.
 * A headless test cannot create a frame, so the tests drive [ToolWindowSetInitializer] without a tool window pane.
 */
@TestApplication
internal class DeferredToolWindowRegistrationTest {
  private val project by projectFixture()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `the factory of an excluded tool window is not asked`(): Unit = timeoutRunBlocking {
    registerTestToolWindows()

    val tasks = computeToolWindowBeans(project) { it == EAGER_ID }

    assertThat(tasks.map { it.id }).containsExactly(EAGER_ID)
    assertThat(DeferredTestToolWindowFactory.createdCount).isZero()
  }

  @Test
  fun `the other tool windows are registered after the frame content paints and before the pending tasks run`(): Unit = timeoutRunBlocking {
    registerTestToolWindows()
    val manager = createManager()
    val events = CopyOnWriteArrayList<String>()
    project.messageBus.connect(disposable).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun toolWindowsRegistered(ids: List<String>, toolWindowManager: ToolWindowManager) {
        events.add("registered $ids")
      }
    })

    initWithDeferredRegistration(manager, deferredRegistrationTimeout = 1.minutes)

    assertThat(manager.getToolWindow(EAGER_ID)).isNotNull()
    // a registration that does not wait for the paint is done by now
    delay(300.milliseconds)
    assertThat(manager.getToolWindow(DEFERRED_ID)).isNull()
    assertThat(DeferredTestToolWindowFactory.createdCount).isZero()

    manager.invokeLater {
      events.add("pending task sees ${manager.getToolWindow(DEFERRED_ID)?.id}")
    }
    events.add("content painted")
    project.getOrCreateFrameContentPaintedDeferred().complete(Unit)
    manager.awaitToolWindowsRegistered()

    assertThat(events).containsExactly(
      "registered [$EAGER_ID]",
      "content painted",
      "registered [$DEFERRED_ID]",
      "pending task sees $DEFERRED_ID",
    )
    assertThat(manager.getEntry(DEFERRED_ID)!!.stripeButton).isNotNull()
  }

  @Test
  fun `the other tool windows are registered when the frame content does not paint`(): Unit = timeoutRunBlocking {
    registerTestToolWindows()
    val manager = createManager()
    val start = TimeSource.Monotonic.markNow()

    initWithDeferredRegistration(manager, deferredRegistrationTimeout = 200.milliseconds)
    manager.awaitToolWindowsRegistered()

    assertThat(manager.getToolWindow(DEFERRED_ID)).isNotNull()
    assertThat(start.elapsedNow().inWholeMilliseconds).isGreaterThanOrEqualTo(200)
  }

  @Test
  fun `the first-run hide covers the tool windows registered after the frame content paints`(): Unit = timeoutRunBlocking {
    registerTestToolWindows()
    val manager = createManager()
    initWithDeferredRegistration(manager)

    applyProjectFrameUiPolicy(
      toolWindowManager = manager,
      project = project,
      projectFrameUiPolicy = ProjectFrameUiPolicy(toolWindowIdsToExclusiveShowing = setOf(EAGER_ID)),
      isStartupToolWindowActivated = false,
    )
    project.getOrCreateFrameContentPaintedDeferred().complete(Unit)
    manager.awaitToolWindowsRegistered()

    assertThat(manager.getEntry(EAGER_ID)!!.stripeButton).isNotNull()
    assertThat(manager.getEntry(DEFERRED_ID)!!.stripeButton).isNull()
  }

  @Test
  fun `the tool windows that the stored layout shows are known before the initialization`() {
    val state = ToolWindowManagerStateImpl()
    val layout = """
      <window_info id="Open" visible="true" show_stripe_button="false"/>
      <window_info id="WithStripeButton"/>
      <window_info id="Closed" show_stripe_button="false"/>
    """.trimIndent()
    state.loadState(JDOMUtil.load("<state><layout>$layout</layout><layoutV2>$layout</layoutV2></state>"))
    project.replaceService(ToolWindowManagerState::class.java, state, disposable)

    assertThat(createManager().getToolWindowIdsShownByStoredLayout()).containsExactlyInAnyOrder("Open", "WithStripeButton")
  }

  @Test
  fun `no tool window is known to be shown without a stored layout`() {
    val state = ToolWindowManagerStateImpl()
    state.noStateLoaded()
    project.replaceService(ToolWindowManagerState::class.java, state, disposable)

    assertThat(createManager().getToolWindowIdsShownByStoredLayout()).isEmpty()
  }

  private fun registerTestToolWindows() {
    DeferredTestToolWindowFactory.createdCount = 0
    ExtensionTestUtil.maskExtensions(
      ToolWindowEP.EP_NAME,
      listOf(
        toolWindowEp(EAGER_ID, EagerTestToolWindowFactory::class.java),
        toolWindowEp(DEFERRED_ID, DeferredTestToolWindowFactory::class.java),
      ),
      disposable,
      fireEvents = false,
    )
    ExtensionTestUtil.maskExtensions(
      ExtensionPointName<RegisterToolWindowTaskProvider>("com.intellij.registerToolWindowTaskProvider"),
      emptyList(),
      disposable,
      fireEvents = false,
    )
    Disposer.register(disposable) {
      val actionManager = ActionManager.getInstance()
      for (id in listOf(EAGER_ID, DEFERRED_ID)) {
        val actionId = ActivateToolWindowAction.Manager.getActionIdForToolWindow(id)
        if (actionManager.getAction(actionId) != null) {
          actionManager.unregisterAction(actionId)
        }
      }
    }
  }

  private fun createManager(): ToolWindowManagerImpl {
    val buttonManager = ToolWindowPaneOldButtonManager(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)
    val coroutineScope = (project as ComponentManagerEx).getCoroutineScope()
    val manager = object : ToolWindowManagerImpl(project, isNewUi = false, isEdtRequired = false, coroutineScope = coroutineScope) {
      override fun getButtonManager(toolWindow: ToolWindow): ToolWindowButtonManager = buttonManager

      override fun getButtonManager(paneId: String): ToolWindowButtonManager = buttonManager
    }
    Disposer.register(disposable, manager)
    return manager
  }

  /**
   * Initializes [manager] the way `ToolWindowManagerImpl.init` does for a welcome-experience frame, without the tool window pane.
   */
  private suspend fun initWithDeferredRegistration(manager: ToolWindowManagerImpl, deferredRegistrationTimeout: Duration = 1.minutes) {
    val layoutManager = ToolWindowDefaultLayoutManager(isNewUi = false)
    layoutManager.noStateLoaded()
    manager.toolWindowSetInitializer.scheduleSetLayout(layoutManager.getLayoutCopy())
    manager.toolWindowSetInitializer.initUi(
      reopeningEditorJob = Job().also { it.complete() },
      taskListDeferred = CompletableDeferred(computeToolWindowBeans(project) { it == EAGER_ID }),
      defersRegistration = true,
      deferredRegistrationTimeout = deferredRegistrationTimeout,
    )
  }
}

private fun toolWindowEp(id: String, factoryClass: Class<out ToolWindowFactory>): ToolWindowEP {
  return ToolWindowEP().apply {
    pluginDescriptor = DefaultPluginDescriptor("test")
    this.id = id
    anchor = ToolWindowAnchor.LEFT.toString()
    this.factoryClass = factoryClass.name
  }
}

internal class EagerTestToolWindowFactory : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
  }
}

internal class DeferredTestToolWindowFactory : ToolWindowFactory {
  companion object {
    @JvmField
    var createdCount: Int = 0
  }

  init {
    createdCount++
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
  }
}
