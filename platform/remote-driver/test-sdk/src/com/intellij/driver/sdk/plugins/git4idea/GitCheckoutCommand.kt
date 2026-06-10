package com.intellij.driver.sdk.plugins.git4idea

import com.intellij.driver.client.Remote

@Remote(value = "com.intellij.vcs.git.performanceTesting.GitCheckoutCommand", plugin = "Git4Idea/intellij.vcs.git.performanceTesting")
interface GitCheckoutCommand {
  fun checkout(branchName: String, newBranchName: String = branchName, alwaysSmartCheckout: Boolean = false): Boolean
}