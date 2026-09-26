// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea

import com.intellij.history.ActivityId
import com.intellij.history.ActivityPresentationProvider
import com.intellij.icons.AllIcons
import icons.DvcsImplIcons
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

class HgActivityPresentationProvider : ActivityPresentationProvider {
  override val id: String get() = ID

  override fun getIcon(kind: String): Icon? {
    return when (kind) {
      HgActivity.Merge.kind -> AllIcons.Actions.ShowAsTree
      HgActivity.Graft.kind -> DvcsImplIcons.CherryPick
      else -> null
    }
  }

  companion object {
    const val ID = "Hg"
  }
}

object HgActivity {
  @JvmField
  val Merge = createId("Merge")

  @JvmField
  val Rebase = createId("Rebase")

  @JvmField
  val Graft = createId("Graft")

  private fun createId(kind: @NonNls String) = ActivityId(HgActivityPresentationProvider.ID, kind)
}