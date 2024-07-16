// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible

import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.graph.PermanentGraph

interface VcsLogFilterer {

  val initialCommitCount: CommitCountStage get() = CommitCountStage.INITIAL

  fun canFilterEmptyPack(filters: VcsLogFilterCollection): Boolean = false

  fun filter(dataPack: DataPack,
             oldVisiblePack: VisiblePack,
             graphOptions: PermanentGraph.Options,
             filters: VcsLogFilterCollection,
             commitCount: CommitCountStage): Pair<VisiblePack, CommitCountStage>
}
