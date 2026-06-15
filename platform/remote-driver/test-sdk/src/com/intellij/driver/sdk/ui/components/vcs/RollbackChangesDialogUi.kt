package com.intellij.driver.sdk.ui.components.vcs

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.JCheckBoxUi

fun Finder.rollbackChangesDialog(
  action: RollbackChangesDialogUi.() -> Unit = {},
): RollbackChangesDialogUi {
  return x(RollbackChangesDialogUi::class.java) {
    contains(byTitle("Revert Changes")) or contains(byTitle("Rollback Changes"))
  }.apply(action)
}

class RollbackChangesDialogUi(data: ComponentData) : DialogUiComponent(data) {

  // The dialog's OK button text comes from VCS-specific RollbackUtil.getRollbackOperationName(project).
  // Git overrides it to "Revert"; default fallback is "Rollback". Match by accessibleName containment
  // so either text resolves.
  override val primaryButtonText: String = "Rollback"
  override val cancelButtonText: String = "Close"

  val changesTree: JChangesListViewUi =
    x(JChangesListViewUi::class.java) { byType("com.intellij.openapi.vcs.changes.ui.ChangesTree") }

  val deleteLocallyAddedCheckbox: JCheckBoxUi =
    x(JCheckBoxUi::class.java) { byAccessibleName("Delete local copies of added files") }

  fun confirm() {
    x {
      byClass("JButton") and
        (byAccessibleName("Revert") or byAccessibleName("Rollback") or
          byAccessibleName("Revert…") or byAccessibleName("Rollback…") or
          byVisibleText("Revert") or byVisibleText("Rollback"))
    }.click()
  }

  fun cancel() {
    x {
      byClass("JButton") and
        (byAccessibleName("Cancel") or byAccessibleName("Close") or
          byVisibleText("Cancel") or byVisibleText("Close"))
    }.click()
  }
}
