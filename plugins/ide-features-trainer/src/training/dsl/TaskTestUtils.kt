// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.dsl

import org.assertj.swing.exception.ComponentLookupException
import org.assertj.swing.fixture.JListFixture
import org.assertj.swing.fixture.JListItemFixture
import training.ui.LearningUiHighlightingManager
import javax.swing.JList

fun TaskTestContext.usePreviouslyFoundListItem(itemAction: (JListItemFixture) -> Unit) {
  val (list, indexFn) = LearningUiHighlightingManager.highlightingComponentsWithInfo.singleOrNull()
                        ?: throw ComponentLookupException("No highlighted list element")
  list as? JList<*> ?: throw ComponentLookupException("No highlighted list element")
  ideFrame {
    val index = indexFn() as? Int ?: throw ComponentLookupException("No index")
    val itemFixture = JListFixture(robot, list).item(index)
    itemAction(itemFixture)
  }
}

fun TaskTestContext.waitAndUsePreviouslyFoundListItem(times: Int = 5, itemAction: (JListItemFixture) -> Unit) {
  for (i in 0 until times) {
    try {
      Thread.sleep(1000)
      usePreviouslyFoundListItem(itemAction)
      return
    } catch (e: ComponentLookupException) {
      if (i == times - 1) {
        throw e
      }
    }
  }
}
