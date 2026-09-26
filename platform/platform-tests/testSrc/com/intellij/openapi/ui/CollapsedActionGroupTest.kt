// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.impl.welcomeScreen.collapsedActionGroup.CollapsedActionGroup
import com.intellij.openapi.wm.impl.welcomeScreen.collapsedActionGroup.ListListenerCollapsedActionGroupExpander
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JList

@TestApplication
@RunInEdt
class CollapsedActionGroupTest {
  private val actionsBeforeCollapseGroup = (1..5).map { MyAction(it) }.toList()
  private val subActions = (6..8).map { MyAction(it) }.toList()
  private val actionsAfterCollapseGroup = (9..12).map { MyAction(it) }.toList()

  private val collapseGroup = CollapsedActionGroup("MyGroup", subActions)

  @Test
  fun listenerOpensActions() {
    val initialModelList = actionsBeforeCollapseGroup + listOf(collapseGroup) + actionsAfterCollapseGroup
    val model = DefaultListModel<AnAction>().apply {
      addAll(initialModelList)
    }
    val list = JList(model)
    ListListenerCollapsedActionGroupExpander.expandCollapsableGroupsOnClick(list, model)

    list.selectedIndex = 1
    MatcherAssert.assertThat("Model broken after selection", model.elements().toList(), Matchers.equalTo(initialModelList))

    list.selectedIndex = initialModelList.size - 1
    MatcherAssert.assertThat("Model broken after selection", model.elements().toList(), Matchers.equalTo(initialModelList))

    val collapsedActionGroupLocation = list.indexToLocation(actionsBeforeCollapseGroup.size)

    val click = MouseEvent(list, MouseEvent.MOUSE_CLICKED,
                           System.currentTimeMillis(), 0,
                           collapsedActionGroupLocation.x,
                           collapsedActionGroupLocation.y,
                           collapsedActionGroupLocation.x,
                           collapsedActionGroupLocation.y,
                           1, false, MouseEvent.BUTTON1)
    list.dispatchEvent(click)  // Click to expand group.

    MatcherAssert.assertThat("Model hasn't been expanded after expanding collapse group", model.elements().toList(), Matchers.equalTo(
      actionsBeforeCollapseGroup + collapseGroup + subActions + actionsAfterCollapseGroup
    ))

    list.dispatchEvent(click) // Click to collapse group.

    MatcherAssert.assertThat("Model hasn't been expanded after collapsing on collapse group", model.elements().toList(), Matchers.equalTo(
      actionsBeforeCollapseGroup + collapseGroup + actionsAfterCollapseGroup
    ))
  }
}

private class MyAction(id: Int) : AnAction("Action$id") {
  override fun actionPerformed(e: AnActionEvent) = Unit
}