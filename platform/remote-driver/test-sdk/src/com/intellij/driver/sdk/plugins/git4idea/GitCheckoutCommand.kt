package com.intellij.driver.sdk.plugins.git4idea

import com.intellij.driver.client.Remote

@Remote(value = "git4idea.performanceTesting.GitCheckoutCommand", plugin = "Git4Idea")
interface GitCheckoutCommand {
  fun checkout(branchName: String): Boolean
}