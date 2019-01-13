// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible

import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.progress.ProgressIndicator
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
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VisiblePackBuilderTest {

  @Test fun `no filters`() {
    val graph = graph {
      1(2) *"master"
      2(3)
      3(4)
      4()
    }
    val visiblePack = graph.build(VcsLogFilterObject.collection())
    assertEquals(4, visiblePack.visibleGraph.visibleCommitCount)
  }

  @Test fun `branch filter`() {
    val graph = graph {
      1(3) *"master"
      2(3) *"feature"
      3(4)
      4()
    }
    val visiblePack = graph.build(VcsLogFilterObject.collection(VcsLogFilterObject.fromBranch("master")))
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
    val visiblePack = graph.build(VcsLogFilterObject.collection(VcsLogFilterObject.fromUser(DEFAULT_USER)))
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
    val visiblePack = graph.build(VcsLogFilterObject.collection(VcsLogFilterObject.fromBranchPatterns(setOf("-master"),
                                                                                                      setOf("master"))))
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
      ArrayList(listOf(2, 3, 4).map { commitId ->
        graph.commits.firstOrNull { commit ->
          commit.id == commitId
        }!!.toVcsCommit(graph.hashMap)
      })
    }

    graph.providers.entries.iterator().next().value.setFilteredCommitsProvider(func)
    val visiblePack = graph.build(VcsLogFilterObject.collection(VcsLogFilterObject.fromBranchPatterns(setOf("-master"), setOf("master")),
                                                                VcsLogFilterObject.fromUser((DEFAULT_USER))))
    val visibleGraph = visiblePack.visibleGraph
    assertEquals(3, visibleGraph.visibleCommitCount)
    assertDoesNotContain(visibleGraph, 1)
  }

  private fun GraphCommit<Int>.toVcsCommit(storage: VcsLogStorage) = TimedVcsCommitImpl(storage.getCommitId(this.id)!!.hash, storage.getHashes(this.parents), 1)

  private fun assertDoesNotContain(graph: VisibleGraph<Int>, id: Int) {
    assertNull((1..graph.visibleCommitCount).firstOrNull { graph.getRowInfo(it - 1).commit == id })
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

        override fun loadCommitsData(hashes: MutableList<Int>,
                                     consumer: Consumer<in MutableList<VcsFullCommitDetails>>,
                                     errorConsumer: Consumer<in Throwable>,
                                     indicator: ProgressIndicator?) {
        }

        override fun getCommitDataIfAvailable(hash: Int): VcsFullCommitDetails? {
          return null
        }
      }
      val builder = VcsLogFiltererImpl(providers, hashMap, detailsCache, commitDetailsGetter, EmptyIndex())

      return builder.filter(dataPack, PermanentGraph.SortType.Normal, filters, CommitCountStage.INITIAL).first
    }

    private fun generateHashMap(num: Int, refs: Set<Ref>, root: VirtualFile): ConstantVcsLogStorage {
      val hashes = HashMap<Int, Hash>()
      for (i in 1..num) {
        hashes[i] = HashImpl.build(i.toString())
      }
      val vcsRefs = refs.mapTo(ArrayList<VcsRef>()) {
        VcsRefImpl(hashes[it.commit]!!, it.name, BRANCH_TYPE, root)
      }
      return ConstantVcsLogStorage(hashes, vcsRefs.indices.map { Pair(it, vcsRefs[it]) }.toMap(), root)
    }

  }

  fun VcsLogStorage.getHashes(ids: List<Int>) = ids.map { getCommitId(it)!!.hash }

  private fun graph(f: GraphBuilder.() -> Unit): Graph {
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

  class ConstantVcsLogStorage(private val hashes: Map<Int, Hash>, val refs: Map<Int, VcsRef>, val root: VirtualFile) : VcsLogStorage {
    private val hashesReversed = hashes.entries.map { Pair(it.value, it.key) }.toMap()
    val refsReversed = refs.entries.map { Pair(it.value, it.key) }.toMap()
    override fun getCommitIndex(hash: Hash, root: VirtualFile) = hashesReversed[hash]!!

    override fun getCommitId(commitIndex: Int) = CommitId(hashes[commitIndex]!!, root)

    override fun containsCommit(id: CommitId): Boolean = root == id.root && hashesReversed.containsKey(id.hash)

    override fun getVcsRef(refIndex: Int): VcsRef = refs[refIndex]!!

    override fun getRefIndex(ref: VcsRef): Int = refsReversed[ref]!!

    override fun iterateCommits(consumer: Function<in CommitId, Boolean>) = throw UnsupportedOperationException()

    override fun flush() {
    }
  }

}

