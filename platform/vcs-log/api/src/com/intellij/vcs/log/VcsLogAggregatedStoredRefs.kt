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
 *  Represents a set of stored references for **all** VCS roots present in the VCS log.
 *
 *  @see VcsLogRootStoredRefs
 */
@ApiStatus.NonExtendable
@ApiStatus.Experimental
interface VcsLogAggregatedStoredRefs : VcsRefsContainer {
  val refsByRoot: Map<VirtualFile, VcsLogRootStoredRefs>

  override fun branches(): Sequence<VcsRef> = refsByRoot.values.asSequence().flatMap { it.branches() }

  override fun tags(): Sequence<VcsRef> = refsByRoot.values.asSequence().flatMap { it.tags() }

  override fun allRefs(): Sequence<VcsRef> = refsByRoot.values.asSequence().flatMap { it.allRefs() }

  /**
   * @return reference that should be displayed for the given "head" (or "tip") commit,
   * representing the branch endpoint
   */
  fun getRefForHeadCommit(headIndex: VcsLogCommitStorageIndex): VcsRef?

  fun getRootForHeadCommit(headIndex: VcsLogCommitStorageIndex): VirtualFile?

  /**
   * @see [VcsLogRootStoredRefs.refsToCommit]
   */
  fun refsToCommit(index: VcsLogCommitStorageIndex): List<VcsRef>
}

val VcsLogAggregatedStoredRefs.branches: List<VcsRef>
  get() = branches().toList()

@ApiStatus.Obsolete
fun VcsLogAggregatedStoredRefs.allRefsStream(): Stream<VcsRef> = allRefs().asStream()

fun VcsLogAggregatedStoredRefs.refsToCommit(root: VirtualFile, index: VcsLogCommitStorageIndex): List<VcsRef> =
  refsByRoot[root]?.refsToCommit(index) ?: emptyList()
