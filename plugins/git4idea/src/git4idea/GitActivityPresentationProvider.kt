// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.history.ActivityId
import com.intellij.history.ActivityPresentationProvider
import com.intellij.icons.AllIcons
import com.intellij.platform.vcs.impl.icons.PlatformVcsImplIcons
import icons.DvcsImplIcons
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

private class GitActivityPresentationProvider : ActivityPresentationProvider {
  override val id: String get() = ID

  override fun getIcon(kind: String): Icon? {
    return when (kind) {
      GitActivity.Merge.kind -> AllIcons.Vcs.Merge
      GitActivity.Stash.kind -> PlatformVcsImplIcons.Stash
      GitActivity.Unstash.kind -> AllIcons.Vcs.Unshelve
      GitActivity.Reset.kind, GitActivity.Revert.kind -> AllIcons.Actions.Rollback
      GitActivity.CherryPick.kind -> DvcsImplIcons.CherryPick
      else -> null
    }
  }

  companion object {
    const val ID = "Git"
  }
}

object GitActivity {
  @JvmField
  val Checkout = createId("Checkout")

  @JvmField
  val Merge = createId("Merge")

  @JvmField
  val Rebase = createId("Rebase")

  @JvmField
  val Abort = createId("Abort")

  @JvmField
  val Reset = createId("Reset")

  @JvmField
  val Stash = createId("Stash")

  val Unstash = createId("Unstash")

  @JvmField
  val CherryPick = createId("CherryPick")

  val Revert = createId("Revert")

  private fun createId(kind: @NonNls String) = ActivityId(GitActivityPresentationProvider.ID, kind)
}