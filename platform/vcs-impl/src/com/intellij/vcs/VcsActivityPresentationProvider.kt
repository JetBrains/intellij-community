// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs

import com.intellij.history.ActivityId
import com.intellij.history.ActivityPresentationProvider
import com.intellij.icons.AllIcons
import com.intellij.platform.vcs.impl.icons.PlatformVcsImplIcons
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

private const val ID = "Vcs"

internal class VcsActivityPresentationProvider : ActivityPresentationProvider {
  override val id: String
    get() = ID

  override fun getIcon(kind: String): Icon? {
    return when (kind) {
      VcsActivity.Commit.kind -> AllIcons.Actions.Commit
      VcsActivity.Rollback.kind -> AllIcons.Actions.Rollback
      VcsActivity.Update.kind -> AllIcons.Actions.CheckOut
      VcsActivity.Get.kind -> AllIcons.Actions.Download
      VcsActivity.Shelve.kind -> PlatformVcsImplIcons.Shelve
      VcsActivity.Unshelve.kind -> AllIcons.Vcs.Unshelve
      VcsActivity.ApplyPatch.kind -> AllIcons.Vcs.Patch
      else -> null
    }
  }
}

object VcsActivity {
  @JvmField
  val Commit = createId("Commit")

  @JvmField
  val Rollback = createId("Rollback")

  @JvmField
  val Update = createId("Update")

  @JvmField
  val Get = createId("Get")

  @JvmField
  val Shelve = createId("Shelve")

  @JvmField
  val Unshelve = createId("Unshelve")

  @JvmField
  val ApplyPatch = createId("ApplyPatch")

  private fun createId(kind: @NonNls String) = ActivityId(ID, kind)
}