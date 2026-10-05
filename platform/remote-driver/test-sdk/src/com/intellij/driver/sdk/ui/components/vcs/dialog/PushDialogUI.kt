package com.intellij.driver.sdk.ui.components.vcs.dialog

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.UiText.Companion.asString
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.JButtonUiComponent
import com.intellij.driver.sdk.ui.components.elements.JCheckboxTreeFixture
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.components.elements.checkBoxTree
import com.intellij.driver.sdk.ui.components.elements.list
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.ui.ui
import java.awt.event.KeyEvent

/**
 * Page object for the VCS "Push Commits to ..." dialog.
 */
fun IdeaFrameUI.pushDialog(action: PushDialogUI.() -> Unit = {}): PushDialogUI =
  x("//div[contains(@title,'Push Commits to')]", PushDialogUI::class.java).apply(action)

class PushDialogUI(data: ComponentData) : DialogUiComponent(data) {

  private val commitTree: JCheckboxTreeFixture = checkBoxTree()
  private val pushButton: JButtonUiComponent = button { and(byAccessibleName("Push"), byClass("MainButton")) }
  private val optionsArrowButton: UiComponent = x { byClass("JBOptionButton") }.x { byClass("ArrowButton") }

  /**
   * Verifies that the dialog lists exactly [commitMessages] (given oldest-to-newest) and nothing
   * else, in that order. The dialog lists commits newest-first, so the expected messages are
   * matched against the rows in reverse.
   */
  fun assertCommitsList(commitMessages: List<String>) {
    val expectedNewestFirst = commitMessages.asReversed()
    shouldBe("Push dialog lists exactly the commits $commitMessages, in order") {
      val displayedCommits = commitTree.collectExpandedPaths()
        .filter { it.path.size > 1 } // commit nodes are children of the repository row
        .sortedBy { it.row } // top-to-bottom, i.e. newest-first
        .map { it.path.last().trim() }
      displayedCommits == expectedNewestFirst
    }
  }

  /**
   * Verifies that [targetBranch] is shown as a new target branch.
   *
   * The remote name is rendered as a separate link component that the tree text reader does not
   * capture, so the dialog row only exposes the (new) target branch.
   */
  fun assertNewTargetBranch(targetBranch: String) {
    shouldBe("New target branch [$targetBranch] is shown in the push dialog") {
      commitTree.collectExpandedPaths().any { row ->
        row.path.joinToString(" ").contains(targetBranch)
      }
    }
  }

  /**
   * Switches the push remote from [currentRemote] to [targetRemote] by clicking the rendered remote
   * link and choosing the remote from the chooser popup. The chooser opens as a top-level window
   * (not a child of the dialog), so it is looked up from the IDE frame. Keep branch names short so
   * the repository row stays narrow and the link is reachable.
   */
  fun selectRemote(targetRemote: String, currentRemote: String = "origin") {
    commitTree.waitOneText(currentRemote).click()
    popup().waitOneText(targetRemote).click()
  }

  /** Renames the (single) repository row's target branch to [targetBranch] via the inline editor. */
  fun renameTargetBranch(targetBranch: String) {
    commitTree.clickRow(0)
    commitTree.keyboard { key(KeyEvent.VK_F2) }
    commitTree.editor().apply {
      click()
      text = targetBranch
    }
    commitTree.keyboard { key(KeyEvent.VK_ENTER) }
  }

  /** Presses the main "Push" button. */
  fun push() {
    pushButton.click()
  }

  /**
   * Verifies that the main button offers [actionName], for example "Push", "Force Push" or "Fetch".
   * The dialog picks the main action after the commits load, so the check waits for it.
   */
  fun assertDefaultAction(actionName: String) {
    shouldBe("The main button of the push dialog is [$actionName]") {
      button { and(byAccessibleName(actionName), byClass("MainButton")) }.present()
    }
  }

  /** Presses the main button, whatever action it offers now. */
  fun clickDefaultAction(actionName: String) {
    step("Press the main button [$actionName] of the push dialog") {
      assertDefaultAction(actionName)
      button { and(byAccessibleName(actionName), byClass("MainButton")) }.click()
    }
  }

  /** Opens the drop-down of the main button and chooses [actionName], for example "Force Push" when the default is "Push". */
  fun chooseAction(actionName: String) {
    step("Choose [$actionName] in the drop-down of the push button") {
      optionsArrowButton.click()
      driver.ui.popup().list().clickItem(actionName)
    }
  }

  /**
   * Verifies that the warning at the top of the dialog contains [fragment].
   * The warning wraps its text, so the check joins the texts of all lines.
   */
  fun assertBannerContains(fragment: String) {
    val banner = x { byClass("InlineBanner") }
    shouldBe("The push dialog warns with [$fragment]") {
      banner.present() && banner.getAllTexts().asString(" ").replace(Regex("\\s+"), " ").contains(fragment)
    }
  }
}
