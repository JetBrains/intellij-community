// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.driver.sdk.ui.components.vcs.dialog

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.shouldBe

/**
 * Page object for the Git "Push Rejected" dialog, which offers to update the branch or to force push.
 */
fun IdeaFrameUI.pushRejectedDialog(action: PushRejectedDialogUI.() -> Unit = {}): PushRejectedDialogUI =
  x("//div[@title='Push Rejected']", PushRejectedDialogUI::class.java).apply(action)

class PushRejectedDialogUI(data: ComponentData) : DialogUiComponent(data) {
  /** Verifies that the message of the dialog contains [fragment]. */
  fun assertMessageContains(fragment: String) {
    shouldBe("The Push Rejected dialog tells [$fragment]") { hasSubtext(fragment) }
  }

  /** Presses the "Force Push" button. */
  fun forcePush() {
    step("Press [Force Push] in the Push Rejected dialog") {
      button { byAccessibleName("Force Push") }.click()
    }
  }
}

/**
 * Page object for the confirmation that the push dialog shows before a force push.
 */
fun IdeaFrameUI.forcePushConfirmation(action: ForcePushConfirmationUI.() -> Unit = {}): ForcePushConfirmationUI =
  x("//div[@title='Force Push']", ForcePushConfirmationUI::class.java).apply(action)

class ForcePushConfirmationUI(data: ComponentData) : DialogUiComponent(data) {
  /** Verifies that the confirmation contains [fragment], for example the number of the replaced commits. */
  fun assertMessageContains(fragment: String) {
    shouldBe("The force push confirmation tells [$fragment]") { hasSubtext(fragment) }
  }

  /** Confirms the force push. */
  fun confirm() {
    step("Confirm the force push") {
      button("Force Push").click()
    }
  }
}
