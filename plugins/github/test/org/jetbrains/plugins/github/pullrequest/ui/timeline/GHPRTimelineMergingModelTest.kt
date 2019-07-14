// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.*
import java.util.*

class GHPRTimelineMergingModelTest : UsefulTestCase() {

  private val actor1 = createTestUser("event_actor1")
  private val actor2 = createTestUser("event_actor2")

  private val currentDate = Date()

  private lateinit var model: GHPRTimelineMergingModel

  override fun setUp() {
    super.setUp()
    model = GHPRTimelineMergingModel()
  }

  fun testMerge() {
    model.add(listOf(
      GHPRAssignedEvent(actor1, currentDate, createTestUser("user")),
      GHPRLabeledEvent(actor1, currentDate, GHLabel.createTest("label")),
      GHPRReviewRequestedEvent(actor1, currentDate, createTestUser("user")),
      GHPRRenamedTitleEvent(actor1, currentDate, "old", "new"),
      GHPRClosedEvent(actor1, currentDate),
      GHPRReopenedEvent(actor1, currentDate),
      GHPRMergedEvent(actor1, currentDate)
    ))

    assertEquals(2, model.size)
  }

  fun testMergeChunked() {
    model.add(listOf(GHPRAssignedEvent(actor1, currentDate, createTestUser("user"))))
    model.add(listOf(GHPRLabeledEvent(actor1, currentDate, GHLabel.createTest("label"))))
    model.add(listOf(GHPRReviewRequestedEvent(actor1, currentDate, createTestUser("user"))))
    model.add(listOf(GHPRRenamedTitleEvent(actor1, currentDate, "old", "new")))

    model.add(listOf(GHPRClosedEvent(actor1, currentDate)))
    model.add(listOf(GHPRReopenedEvent(actor1, currentDate)))
    model.add(listOf(GHPRMergedEvent(actor1, currentDate)))

    assertEquals(2, model.size)
  }

  fun testMergeCollapse() {
    model.add(listOf(
      GHPRAssignedEvent(actor1, currentDate, createTestUser("user")),
      GHPRLabeledEvent(actor1, currentDate, GHLabel.createTest("label")),
      GHPRReviewRequestedEvent(actor1, currentDate, createTestUser("user")),
      GHPRRenamedTitleEvent(actor1, currentDate, "old", "new"),

      GHPRUnassignedEvent(actor1, currentDate, createTestUser("user")),
      GHPRUnlabeledEvent(actor1, currentDate, GHLabel.createTest("label")),
      GHPRReviewUnrequestedEvent(actor1, currentDate, createTestUser("user")),
      GHPRRenamedTitleEvent(actor1, currentDate, "new", "old"),

      GHPRClosedEvent(actor1, currentDate),

      GHPRReopenedEvent(actor1, currentDate)
    ))

    assertEquals(0, model.size)
  }

  fun testMergeCollapseChunked() {
    model.add(listOf(GHPRAssignedEvent(actor1, currentDate, createTestUser("user"))))
    model.add(listOf(GHPRLabeledEvent(actor1, currentDate, GHLabel.createTest("label"))))
    model.add(listOf(GHPRReviewRequestedEvent(actor1, currentDate, createTestUser("user"))))
    model.add(listOf(GHPRRenamedTitleEvent(actor1, currentDate, "old", "new")))

    model.add(listOf(GHPRUnassignedEvent(actor1, currentDate, createTestUser("user"))))
    model.add(listOf(GHPRUnlabeledEvent(actor1, currentDate, GHLabel.createTest("label"))))
    model.add(listOf(GHPRReviewUnrequestedEvent(actor1, currentDate, createTestUser("user"))))
    model.add(listOf(GHPRRenamedTitleEvent(actor1, currentDate, "new", "old")))

    model.add(listOf(GHPRClosedEvent(actor1, currentDate)))

    model.add(listOf(GHPRReopenedEvent(actor1, currentDate)))

    assertEquals(0, model.size)
  }

  fun testNonMerge() {
    model.add(listOf(
      GHPRAssignedEvent(actor1, Date(currentDate.time - DateFormatUtil.YEAR), createTestUser("user")),
      //date difference
      GHPRAssignedEvent(actor1, currentDate, createTestUser("user2")),
      //type difference
      GHPRBaseRefChangedEvent(actor1, currentDate),
      //type difference
      GHPRAssignedEvent(actor1, currentDate, createTestUser("user3")),
      //type difference
      GHPRClosedEvent(actor1, currentDate),
      //date difference
      GHPRReopenedEvent(actor1, Date(currentDate.time + DateFormatUtil.YEAR)),
      //actor difference
      GHPRMergedEvent(actor2, Date(currentDate.time + DateFormatUtil.YEAR))
    ))
    assertEquals(7, model.size)
  }

  fun testNonMergeChunked() {
    model.add(listOf(GHPRAssignedEvent(actor1, Date(currentDate.time - DateFormatUtil.YEAR), createTestUser("user"))))
    model.add(listOf(GHPRAssignedEvent(actor1, currentDate, createTestUser("user2"))))
    model.add(listOf(GHPRBaseRefChangedEvent(actor1, currentDate)))
    model.add(listOf(GHPRAssignedEvent(actor1, currentDate, createTestUser("user3"))))
    model.add(listOf(GHPRClosedEvent(actor1, currentDate)))
    model.add(listOf(GHPRReopenedEvent(actor1, Date(currentDate.time + DateFormatUtil.YEAR))))
    model.add(listOf(GHPRMergedEvent(actor2, Date(currentDate.time + DateFormatUtil.YEAR))))
    assertEquals(7, model.size)
  }

  companion object {
    private fun createTestUser(id: String) = GHUser(id, "testUser_$id", "", "", null)
  }
}