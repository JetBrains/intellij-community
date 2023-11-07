// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem

import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertOrderedEquals
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.RunMethodInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.DelicateCoroutinesApi
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(DelicateCoroutinesApi::class)
@TestApplication
@RunInEdt(allMethods = false)
class ActionUpdaterTest {

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
