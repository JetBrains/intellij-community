// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertOrderedEquals
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.RunMethodInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ObjectUtils
import com.intellij.util.TimeoutUtil
import com.intellij.util.application
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
@RunInEdt(allMethods = false)
class ActionUpdaterTest {

  @BeforeEach
  internal fun setUp() {
    ActionManager.getInstance() // preload ActionManager
  }

  @Test
  @RunMethodInEdt
  fun testActionGroupCanBePerformed() {
    val canBePerformedGroup: ActionGroup = newCanBePerformedGroup(true, true)
    val popupGroup = newPopupGroup(canBePerformedGroup)
    val actionGroup: ActionGroup = DefaultActionGroup(popupGroup)
    val actions = expandActionGroup(actionGroup)
    assertOrderedEquals(actions, popupGroup)
  }

  @Test
  @RunMethodInEdt
  fun testActionGroupCanBePerformedButNotVisible() {
    val canBePerformedGroup: ActionGroup = newCanBePerformedGroup(false, false)
    val actionGroup: ActionGroup = DefaultActionGroup(newPopupGroup(canBePerformedGroup))
    val actions = expandActionGroup(actionGroup)
    assertEmpty(actions)
  }

  @Test
  @RunMethodInEdt
  fun testActionGroupCanBePerformedButNotEnabled() {
    val canBePerformedGroup: ActionGroup = newCanBePerformedGroup(true, false)
    val actionGroup: ActionGroup = DefaultCompactActionGroup(newPopupGroup(canBePerformedGroup))
    val actions = expandActionGroup(actionGroup)
    assertEmpty(actions)
  }

  @Test
  @RunMethodInEdt
  fun testWrappedActionGroupHasCorrectPresentation() {
    val customizedText = "Customized!"
    val presentationFactory = PresentationFactory()
    val popupGroup: ActionGroup = object : DefaultActionGroup(newCanBePerformedGroup(true, true)) {
      override fun update(e: AnActionEvent) {
        e.presentation.text = customizedText
      }
    }
    popupGroup.templatePresentation.isPopupGroup = true
    val actions = expandActionGroup(DefaultCompactActionGroup(popupGroup), presentationFactory)
    val actual = ContainerUtil.getOnlyItem(actions)
    assertTrue(actual is ActionGroupWrapper && actual.delegate === popupGroup, "wrapper expected")
    val actualPresentation = presentationFactory.getPresentation(actual!!)
    assertSame(customizedText, actualPresentation.text)
    assertSame(actualPresentation, presentationFactory.getPresentation(popupGroup))
  }

  @Test
  fun testFastTrackWorksAsExpected() = timeoutRunBlocking {
    // scenario: call expandGroup that takes 100+ ms with 3 s fast-track
    // expected: the result is synchronous, and takes just a bit more than 100 ms
    val fastTrack = Registry.get("actionSystem.update.actions.async.fast-track.timeout.ms")
    val prevFastTrack = fastTrack.asInteger()
    assertTrue(prevFastTrack < 100, "The default fast-track must be < 100 to proceed, actual $prevFastTrack")
    val start = System.nanoTime()
    var jobCompleted = false
    val actions = try {
      fastTrack.setValue(3_000)
      val group = object : ActionGroup() {
        override fun getChildren(e: AnActionEvent?): Array<out AnAction?> {
          ProgressIndicatorUtils.awaitWithCheckCanceled(100)
          return arrayOf(EmptyAction.createEmptyAction("", null, true))
        }
      }
      withContext(Dispatchers.EDT) {
        val count = IdeEventQueue.getInstance().eventCount
        val result = async(start = CoroutineStart.UNDISPATCHED) {
          Utils.expandActionGroupSuspend(group, PresentationFactory(), DataContext.EMPTY_CONTEXT,
                                         ActionPlaces.UNKNOWN, false, fastTrack = true)
        }
        jobCompleted = result.isCompleted
        assertEquals(count, IdeEventQueue.getInstance().eventCount, "Expand must complete in a single event")
        result.await()
      }
    }
    finally {
      fastTrack.setValue(prevFastTrack)
    }
    val millis = TimeoutUtil.getDurationMillis(start)
    assertTrue(millis > 100, "The update must call getChildren and take more than 100 ms, actual $millis ms (jobCompleted=$jobCompleted)")
    assertTrue(millis < 500, "The update must not take much more than ~100 ms, actual $millis ms (jobCompleted=$jobCompleted)")
    assertTrue(jobCompleted, "The update job must be synchronously completed by fast-track")
    assertEquals(1, actions.size)
  }

  @Test
  fun testSessionComputeOnEDTWorksAsExpected() = timeoutRunBlocking {
    // scenario: call expandGroup that schedules a block on EDT in RA while WA is requested
    // expected: RA is cancelled, WA is performed, and the expansion is retried
    var getChildrenCount = 0
    var supplierCount = 0
    val semaphore = Semaphore(1, 1)
    val action = EmptyAction.createEmptyAction("", null, true)
    val actionGroup = object : ActionGroup() {
      override fun getChildren(e: AnActionEvent?): Array<out AnAction?> {
        e!!
        if (getChildrenCount++ == 0) {
          semaphore.release()
        }
        assertFalse(EDT.isCurrentThreadEdt(), "Must not be in EDT")
        assertTrue(application.isReadAccessAllowed(), "Must be in RA")
        return e.updateSession.compute(this, "op2", ActionUpdateThread.EDT) {
          supplierCount++
          assertTrue(EDT.isCurrentThreadEdt(), "Must be in EDT")
          assertTrue(application.isReadAccessAllowed(), "Must be in RA")
          arrayOf<AnAction>(action)
        }
      }
    }
    withContext(Dispatchers.EDT) {
      val result = async(start = CoroutineStart.UNDISPATCHED) {
        Utils.expandActionGroupSuspend(actionGroup, PresentationFactory(), DataContext.EMPTY_CONTEXT,
                                       ActionPlaces.UNKNOWN, false, fastTrack = false)
      }
      assertFalse(result.isCompleted, "The update must still be in progress")
      semaphore.acquire()
      val waIsExecuted = writeAction {
        true
      }
      val actions = result.await()
      assertEquals(1, actions.size)
      assertEquals(2, getChildrenCount, "getChildrenCount must be retried due to WA")
      assertEquals(1, supplierCount, "supplier must be called just once")
      assertTrue(waIsExecuted)
    }
  }

  @Test
  fun testSessionSharedDataWorksInEDTAsExpected() = timeoutRunBlocking {
    // scenario: call expandGroup on EDT that tries to get sharedData a couple of times
    // expected: works, does not corrupt the update session by AwaitSharedData exception
    val key1 = Key.create<Int>("Key1")
    val key2 = Key.create<Int>("Key2")
    var getChildrenCount = 0
    var supplierCount = 0
    val action = EmptyAction.createEmptyAction("", null, true)
    val actionGroup = object : ActionGroup() {
      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
      override fun getChildren(e: AnActionEvent?): Array<out AnAction?> {
        e!!
        getChildrenCount ++
        e.updateSession.sharedData(key1) { supplierCount++; 1 }
        e.updateSession.sharedData(key1) { supplierCount++; 1 }
        e.updateSession.sharedData(key2) { supplierCount++; 2 }
        e.updateSession.sharedData(key2) { supplierCount++; 2 }
        return arrayOf<AnAction>(action)
      }
    }
    val actions = try {
      withContext(Dispatchers.EDT) {
        Utils.expandActionGroupSuspend (actionGroup, PresentationFactory(), DataContext.EMPTY_CONTEXT,
                                        ActionPlaces.UNKNOWN, false, fastTrack = false)
      }
    }
    catch (ex: Exception) {
      fail("Update session must not fail with `${ex::class.simpleName}`", ex)
    }
    assertEquals(1, actions.size)
    assertEquals(3, getChildrenCount, "getChildren must be invoked 3 times due retries")
    assertEquals(2, supplierCount, "Suppliers must be invoked 2 times, one for each")
  }

  @Test
  fun testSessionComputeOnEDTWorksInModalContext() = timeoutRunBlocking {
    val actionGroup = object : ActionGroup() {
      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
      override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        assertTrue(EDT.isCurrentThreadEdt(), "Must be in EDT")
        return arrayOf<AnAction>(EmptyAction.createEmptyAction("", null, true))
      }
    }
    val actions = withContext(Dispatchers.EDT) {
      val modalEntity = ObjectUtils.sentinel("modality")
      LaterInvocator.enterModal(modalEntity)
      try {
        Utils.expandActionGroupSuspend(actionGroup, PresentationFactory(), DataContext.EMPTY_CONTEXT,
                                       ActionPlaces.UNKNOWN, false, fastTrack = false)
      }
      finally {
        LaterInvocator.leaveModal(modalEntity)
      }
    }
    assertEquals(1, actions.size)
  }

  private fun expandActionGroup(actionGroup: ActionGroup,
                                presentationFactory: PresentationFactory = PresentationFactory()): List<AnAction?> {
    return Utils.expandActionGroup(actionGroup, presentationFactory, DataContext.EMPTY_CONTEXT, ActionPlaces.UNKNOWN)
  }

  private fun newPopupGroup(vararg actions: AnAction): ActionGroup {
    val group = DefaultActionGroup(*actions)
    group.templatePresentation.isPopupGroup = true
    group.templatePresentation.isHideGroupIfEmpty = true
    return group
  }

  private fun newCanBePerformedGroup(visible: Boolean, enabled: Boolean): DefaultActionGroup {
    return object : DefaultActionGroup() {
      override fun update(e: AnActionEvent) {
        e.presentation.isVisible = visible
        e.presentation.isEnabled = enabled
        e.presentation.isPerformGroup = true
      }
    }
  }
}
