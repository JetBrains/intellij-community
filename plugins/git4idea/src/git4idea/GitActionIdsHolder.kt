// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionIdsHolder

class GitActionIdsHolder : ActionIdsHolder {

  enum class Id(@JvmField val id: String) {

    // rebase process
    ABORT("git4idea.rebase.abort"),
    CONTINUE("git4idea.rebase.continue"),
    RETRY("git4idea.rebase.retry"),
    RESOLVE("git4idea.rebase.resolve"),
    STAGE_AND_RETRY("git4idea.rebase.stage.and.retry"),
  }

  override fun getActionsIds(): List<String> = Id.entries.map(Id::id).toList()
}
