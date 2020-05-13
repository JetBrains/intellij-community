// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.DataKey
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import java.util.function.Consumer

object GHPRActionKeys {
  @JvmStatic
  val ACTION_DATA_CONTEXT = DataKey.create<GHPRActionDataContext>("org.jetbrains.plugins.github.pullrequest.actiondatacontext")

  @JvmStatic
  internal val DATA_CONTEXT = DataKey.create<GHPRDataContext>("org.jetbrains.plugins.github.pullrequest.datacontext")

  @JvmStatic
  internal val VIEW_PULL_REQUEST_EXECUTOR = DataKey.create<Consumer<GHPullRequestShort>>(
    "org.jetbrains.plugins.github.pullrequest.view.executor")

  @JvmStatic
  internal val SELECTED_PULL_REQUEST = DataKey.create<GHPullRequestShort>("org.jetbrains.plugins.github.pullrequest.list.selected")
}