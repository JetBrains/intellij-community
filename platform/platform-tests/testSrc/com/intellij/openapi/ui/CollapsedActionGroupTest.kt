// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.impl.welcomeScreen.collapsedActionGroup.CollapsedActionGroup
import com.intellij.openapi.wm.impl.welcomeScreen.collapsedActionGroup.ListListenerCollapsedActionGroupExpander
import com.intellij.openapi.wm.impl.welcomeScreen.collapsedActionGroup.createCollapsedButton
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import javax.swing.DefaultListModel
import javax.swing.JList

@TestApplication
@RunInEdt
class CollapsedActionGroupTest {
  @TestDisposable
  private lateinit var disposable: Disposable

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
    ListListenerCollapsedActionGroupExpander.expandCollapsableGroupsOnSelection(list, model, disposable)

    list.selectedIndex = 1
    MatcherAssert.assertThat("Model broken after selection", model.elements().toList(), Matchers.equalTo(initialModelList))

    list.selectedIndex = initialModelList.size - 1
    MatcherAssert.assertThat("Model broken after selection", model.elements().toList(), Matchers.equalTo(initialModelList))

    list.selectedIndex = actionsBeforeCollapseGroup.size // Click on collapse grop
    MatcherAssert.assertThat("Model hasn't been expanded after clicking on collapse button", model.elements().toList(), Matchers.equalTo(
      actionsBeforeCollapseGroup + subActions + actionsAfterCollapseGroup
    ))
  }

  @Test
  fun collapsedButtonWidth() {
    val componentWidth = createCollapsedButton(collapseGroup) {
      (it as MyAction).preferedWidth
    }.preferredSize.width
    val maxActionWidth = subActions.maxOfOrNull { it.preferedWidth }!!
    MatcherAssert.assertThat(
      "Component size should be at least as wide as longest action not to blink when actions appear",
      componentWidth, Matchers.greaterThanOrEqualTo(maxActionWidth))
  }
}

private class MyAction(id: Int) : AnAction("Action$id") {
  override fun actionPerformed(e: AnActionEvent) = Unit
  val preferedWidth = id * 100
}