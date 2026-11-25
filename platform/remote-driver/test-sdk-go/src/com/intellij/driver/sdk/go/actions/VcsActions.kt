package com.intellij.driver.sdk.go.actions

import com.intellij.driver.client.Driver
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.git4idea.GitCheckoutCommand
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class VcsActions(val driver: Driver, val ui: UiRobot) {

  /**
   * Creates [branchName] from [startPoint] and checks it out. [startPoint] may be a branch, a tag or a commit hash;
   * by default it is [branchName] itself, so an already existing branch is simply checked out.
   *
   * The checkout runs on the backend, where the Git repositories of the project are.
   */
  fun checkoutBranch(branchName: String, startPoint: String = branchName, indexingTimeout: Duration = 5.minutes) {
    step("Checkout branch '$branchName' from '$startPoint'") {
      var failure: Throwable? = null
      waitFor(
        message = "Branch '$branchName' is checked out",
        timeout = 2.minutes,
        errorMessage = { "The checkout of '$branchName' failed: ${failure?.message ?: "the command returned false"}" },
      ) {
        // Retry on both a `false` result and an exception: right after project open the Git repository
        // mapping may not be resolvable yet, in which case the command rejects and throws.
        runCatching {
          driver.withContext { new(GitCheckoutCommand::class, rdTarget = RdTarget.BACKEND).checkout(branchName = startPoint, newBranchName = branchName) }
        }.onSuccess { failure = null }.onFailure { failure = it }.getOrDefault(false)
      }
      driver.waitForIndicators(indexingTimeout)
    }
  }
}
