// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.ui

import com.intellij.openapi.actionSystem.DataKey

object GitStageDataKeys {
  @JvmField
  val GIT_FILE_STATUS_NODES = DataKey.create<Iterable<GitFileStatusNode>>("GitFileStatusNodesStream")
  @JvmField
  val GIT_STAGE_TREE = DataKey.create<GitStageTree>("GitStageTree")
  @JvmField
  val GIT_STAGE_UI_SETTINGS = DataKey.create<GitStageUiSettings>("GitStageUiSettings")
}
