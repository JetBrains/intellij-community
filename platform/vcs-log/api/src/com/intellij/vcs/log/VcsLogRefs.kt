/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.util.stream.Stream
import kotlin.streams.asStream

/**
 *  Represents a set of references for **all** VCS roots present in the VCS log.
 *
 *  @see VcsLogRefsOfSingleRoot
 */
@ApiStatus.NonExtendable
interface VcsLogRefs {
  val refsByRoot: Map<VirtualFile, VcsLogRefsOfSingleRoot>

  /**
   * @return reference that should be displayed for the given "head" (or "tip") commit,
   * representing the branch endpoint
   */
  fun getRefForHeadCommit(headIndex: VcsLogCommitStorageIndex): VcsRef?

  fun getRootForHeadCommit(headIndex: VcsLogCommitStorageIndex): VirtualFile?

  /**
   * @see [VcsLogRefsOfSingleRoot.refsToCommit]
   */
  fun refsToCommit(index: VcsLogCommitStorageIndex): List<VcsRef>
}

val VcsLogRefs.allRefs: Sequence<VcsRef>
  get() = refsByRoot.values.asSequence().flatMap(VcsLogRefsOfSingleRoot::getRefs)

val VcsLogRefs.branches: List<VcsRef>
  get() = refsByRoot.values.flatMapTo(mutableListOf()) {
    it.getBranches()
  }

@ApiStatus.Obsolete
fun VcsLogRefs.allRefsStream(): Stream<VcsRef> =
  refsByRoot.values.asSequence().flatMap(VcsLogRefsOfSingleRoot::getRefs).asStream()

fun VcsLogRefs.refsToCommit(root: VirtualFile, index: VcsLogCommitStorageIndex): List<VcsRef> =
  refsByRoot[root]?.refsToCommit(index) ?: emptyList()
