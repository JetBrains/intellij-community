// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [GitBranchReviewPresenter.Review] must compare by [title] only, ignoring the
 * [open][GitBranchReviewPresenter.Review.open] callback. Two lookups that resolve to the same review build a
 * new lambda each time, so including it in [equals]/[hashCode] would make [kotlinx.coroutines.flow.StateFlow] treat
 * every re-lookup as a change and repaint a worktree row that has not actually changed.
 */
internal class GitBranchReviewPresenterTest {
  @Test
  fun `test two reviews with the same title are equal despite distinct open callbacks`() {
    val first = GitBranchReviewPresenter.Review("Add feature") {}
    val second = GitBranchReviewPresenter.Review("Add feature") {}

    assertThat(first).isEqualTo(second)
    assertThat(first.hashCode()).isEqualTo(second.hashCode())
  }

  @Test
  fun `test a different title makes two reviews unequal`() {
    val first = GitBranchReviewPresenter.Review("Add feature") {}
    val second = GitBranchReviewPresenter.Review("Fix bug") {}

    assertThat(first).isNotEqualTo(second)
  }

  @Test
  fun `test open invokes this instance's own callback, not another equal instance's`() {
    var firstOpened = false
    var secondOpened = false
    val first = GitBranchReviewPresenter.Review("Add feature") { firstOpened = true }
    val second = GitBranchReviewPresenter.Review("Add feature") { secondOpened = true }

    // Equal reviews still each carry their own callback - equality is only a de-duplication key.
    assertThat(first).isEqualTo(second)
    second.open()

    assertThat(secondOpened).describedAs("Calling open() on the second instance must run its own callback").isTrue()
    assertThat(firstOpened).describedAs("Calling open() on the second instance must not run the first instance's callback").isFalse()
  }
}
