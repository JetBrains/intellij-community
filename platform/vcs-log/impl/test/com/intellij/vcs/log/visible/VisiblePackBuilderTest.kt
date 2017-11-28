/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.visible

import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.util.Function
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.*
import com.intellij.vcs.log.graph.GraphCommit
import com.intellij.vcs.log.graph.GraphCommitImpl
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.graph.VisibleGraph
import com.intellij.vcs.log.impl.*
import com.intellij.vcs.log.impl.TestVcsLogProvider.BRANCH_TYPE
import com.intellij.vcs.log.impl.TestVcsLogProvider.DEFAULT_USER
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl.VcsLogFilterCollectionBuilder
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VisiblePackBuilderTest {

  @Test fun `no filters`() {
    val graph = graph {
      1(2) *"master"
      2(3)
      3(4)
      4()
    }
    val visiblePack = graph.build(noFilters())
    assertEquals(4, visiblePack.visibleGraph.visibleCommitCount)
  }

  @Test fun `branch filter`() {
    val graph = graph {
      1(3) *"master"
      2(3) *"feature"
      3(4)
      4()
    }
    val visiblePack = graph.build(filters(branch = listOf("master")))
    val visibleGraph = visiblePack.visibleGraph
    assertEquals(3, visibleGraph.visibleCommitCount)
    assertDoesNotContain(visibleGraph, 2)
  }

  @Test fun `filter by user in memory`() {
    val graph = graph {
      1(2) *"master"
      2(3)
      3(4)           +"bob.doe"
      4(5)
      5(6)
      6(7)
      7()
    }
    val visiblePack = graph.build(filters(user = DEFAULT_USER))
    val visibleGraph = visiblePack.visibleGraph
    assertEquals(6, visibleGraph.visibleCommitCount)
    assertDoesNotContain(visibleGraph, 3)
  }

  @Test fun `filter by branch deny`() {
    val graph = graph {
      1(3) *"master"
      2(3) *"feature"
      3(4)
      4()
    }
    val visiblePack = graph.build(filters(VcsLogBranchFilterImpl.fromTextPresentation(setOf("-master"), setOf("master"))))
    val visibleGraph = visiblePack.visibleGraph
    assertEquals(3, visibleGraph.visibleCommitCount)
    assertDoesNotContain(visibleGraph, 1)
  }

  @Test fun `filter by branch deny works with extra results from vcs provider`() {
    val graph = graph {
      1(3) *"master"  +null
      2(3) *"feature" +null
      3(4)             +null
      4()              +null
    }

    val func = Function<VcsLogFilterCollection, MutableList<TimedVcsCommit>> {
      ArrayList(listOf(2, 3, 4).map {
        val id = it
        val commit = graph.commits.firstOrNull {
          it.id == id
        }
        commit!!.toVcsCommit(graph.hashMap)
      })
    }

    graph.providers.entries.iterator().next().value.setFilteredCommitsProvider(func)
    val visiblePack = graph.build(filters(VcsLogBranchFilterImpl.fromTextPresentation(setOf("-master"), setOf("master")), userFilter(DEFAULT_USER)))
    val visibleGraph = visiblePack.visibleGraph
    assertEquals(3, visibleGraph.visibleCommitCount)
    assertDoesNotContain(visibleGraph, 1)
  }

  private fun GraphCommit<Int>.toVcsCommit(storage: VcsLogStorage) = TimedVcsCommitImpl(storage.getCommitId(this.id)!!.hash, storage.getHashes(this.parents), 1)

  fun assertDoesNotContain(graph: VisibleGraph<Int>, id: Int) {
    assertTrue(null == (1..graph.visibleCommitCount).firstOrNull { graph.getRowInfo(it - 1).commit == id })
  }

  data class Ref(val name: String, val commit: Int)
  data class Data(val user: VcsUser? = DEFAULT_USER, val subject: String = "default commit message")

  inner class Graph(val commits: List<GraphCommit<Int>>,
                    val refs: Set<Ref>,
                    val data: HashMap<GraphCommit<Int>, Data>) {
    val root: VirtualFile = MockVirtualFile("root")
    val providers: Map<VirtualFile, TestVcsLogProvider> = mapOf(root to TestVcsLogProvider(root))
    val hashMap = generateHashMap(commits.maxBy { it.id }!!.id, refs, root)

    fun build(filters: VcsLogFilterCollection): VisiblePack {
      val dataPack = DataPack.build(commits, mapOf(root to hashMap.refsReversed.keys).mapValues { CompressedRefs(it.value, hashMap) }, providers, hashMap, true)
      val detailsCache = TopCommitsCache(hashMap)
      detailsCache.storeDetails(ArrayList(data.entries.mapNotNull {
        val hash = hashMap.getCommitId(it.key.id).hash
        if (it.value.user == null)
          null
        else VcsCommitMetadataImpl(hash, hashMap.getHashes(it.key.parents), 1L, root, it.value.subject,
            it.value.user!!, it.value.subject, it.value.user!!, 1L)
      }))

      val commitDetailsGetter = object : DataGetter<VcsFullCommitDetails> {
        override fun getCommitData(row: Int, neighbourHashes: MutableIterable<Int>): VcsFullCommitDetails {
          throw UnsupportedOperationException()
        }

        override fun loadCommitsData(hashes: MutableList<Int>, consumer: Consumer<MutableList<VcsFullCommitDetails>>, indicator: ProgressIndicator?) {
        }

        override fun getCommitDataIfAvailable(hash: Int): VcsFullCommitDetails? {
          return null
        }
      }
      val builder = VcsLogFilterer(providers, hashMap, detailsCache, commitDetailsGetter, EmptyIndex())

      return builder.filter(dataPack, PermanentGraph.SortType.Normal, filters, CommitCountStage.INITIAL).first
    }

    fun generateHashMap(num: Int, refs: Set<Ref>, root: VirtualFile): ConstantVcsLogStorage {
      val hashes = HashMap<Int, Hash>()
      for (i in 1..num) {
        hashes.put(i, HashImpl.build(i.toString()))
      }
      val vcsRefs = refs.mapTo(ArrayList<VcsRef>(), {
        VcsRefImpl(hashes[it.commit]!!, it.name, BRANCH_TYPE, root)
      })
      return ConstantVcsLogStorage(hashes, vcsRefs.indices.map { Pair(it, vcsRefs[it]) }.toMap(), root)
    }

  }

  fun VcsLogStorage.getHashes(ids: List<Int>) = ids.map { getCommitId(it)!!.hash }

  fun noFilters(): VcsLogFilterCollection = VcsLogFilterCollectionBuilder().build()

  fun filters(branch: VcsLogBranchFilter? = null, user: VcsLogUserFilter? = null)
      = VcsLogFilterCollectionBuilder().with(branch).with(user).build()

  fun filters(branch: List<String>? = null, user: VcsUser? = null)
      = VcsLogFilterCollectionBuilder().with(branchFilter(branch)).with(userFilter(user)).build()

  fun branchFilter(branch: List<String>?): VcsLogBranchFilterImpl? {
    return if (branch != null) VcsLogBranchFilterImpl.fromTextPresentation(branch, branch.toHashSet()) else null
  }

  fun userFilter(user: VcsUser?): VcsLogUserFilter? {
    return if (user != null) VcsLogUserFilterImpl(listOf(user.name), emptyMap(), setOf(user)) else null
  }

  fun graph(f: GraphBuilder.() -> Unit): Graph {
    val builder = GraphBuilder()
    builder.f()
    return builder.done()
  }

  inner class GraphBuilder {
    val commits = ArrayList<GraphCommit<Int>>()
    val refs = HashSet<Ref>()
    val data = HashMap<GraphCommit<Int>, Data>()

    operator fun Int.invoke(vararg id: Int): GraphCommit<Int> {
      val commit = GraphCommitImpl.createCommit(this, id.toList(), this.toLong())
      commits.add(commit)
      data[commit] = Data()
      return commit
    }

    operator fun GraphCommit<Int>.times(name: String): GraphCommit<Int> {
      refs.add(Ref(name, this.id))
      return this
    }

    operator fun GraphCommit<Int>.plus(name: String): GraphCommit<Int> {
      data[this] = Data(VcsUserImpl(name, name + "@example.com"))
      return this
    }

    operator fun GraphCommit<Int>.plus(user: VcsUser?): GraphCommit<Int> {
      data[this] = Data(user)
      return this
    }

    fun done() = Graph(commits, refs, data)
  }

  class ConstantVcsLogStorage(val hashes: Map<Int, Hash>, val refs: Map<Int, VcsRef>, val root: VirtualFile) : VcsLogStorage {
    val hashesReversed = hashes.entries.map { Pair(it.value, it.key) }.toMap()
    val refsReversed = refs.entries.map { Pair(it.value, it.key) }.toMap()

    override fun getCommitIndex(hash: Hash, root: VirtualFile) = hashesReversed[hash]!!

    override fun getCommitId(commitIndex: Int) = CommitId(hashes[commitIndex]!!, root)

    override fun getVcsRef(refIndex: Int): VcsRef = refs[refIndex]!!

    override fun getRefIndex(ref: VcsRef): Int = refsReversed[ref]!!

    override fun findCommitId(condition: Condition<CommitId>): CommitId? = throw UnsupportedOperationException()

    override fun flush() {
    }
  }

}

