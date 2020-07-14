// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible

import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TemporaryDirectory
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
import com.intellij.vcs.log.util.VcsLogUtil.FULL_HASH_LENGTH
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import org.junit.Rule
import org.junit.Test
import java.util.*
import kotlin.random.nextInt
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VisiblePackBuilderTest {
  @Rule
  @JvmField
  val tempDir = TemporaryDirectory()

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

    val provider = object : TestVcsLogProvider() {
      override fun getCommitsMatchingFilter(root: VirtualFile,
                                            filterCollection: VcsLogFilterCollection,
                                            maxCount: Int): List<TimedVcsCommit> {
        return listOf(2, 3, 4).map { commitId ->
          graph.allCommits.first { it.id == commitId }.toVcsCommit(graph.hashMap)
        }
      }
    }
    graph.providers = graph.roots.associateWith { provider }

    val visiblePack = graph.build(VcsLogFilterObject.collection(VcsLogFilterObject.fromBranchPatterns(setOf("-master"), setOf("master")),
                                                                VcsLogFilterObject.fromUser((DEFAULT_USER))))
    val visibleGraph = visiblePack.visibleGraph
    assertEquals(3, visibleGraph.visibleCommitCount)
    assertDoesNotContain(visibleGraph, 1)
  }

  @Test
  fun `filter by range`() {
    val graph = graph {
      1(3) *"master"
      2(3) *"feature"
      3(4)
      4()
    }
    val filters = VcsLogFilterObject.collection(VcsLogFilterObject.fromRange("master", "feature"))

    val visiblePack = graph.build(filters)
    assertCommits(visiblePack.visibleGraph, 2)
  }

  @Test
  fun `filter by range and branch should unite commits matching the range with commits reachable from the branch`() {
    val graph = graph {
      6(5) *"183"
      5(4)
      1(3) *"master"
      2(3) *"feature"
      3(4)
      4()
    }
    val filters = VcsLogFilterObject.collection(VcsLogFilterObject.fromRange("master", "feature"), VcsLogFilterObject.fromBranch("183"))

    val visiblePack = graph.build(filters)
    assertCommits(visiblePack.visibleGraph, 6, 5, 2, 4)
  }

  @Test
  fun `filter by range and structure filter`() {
    val graph = graph {
      1(3) *"master"
      2(3) *"feature"
      3(4)
      4()
    }

    val filePath = object : LocalFilePath(tempDir.createDir(), true) {
      override fun getVirtualFile(): VirtualFile? {
        return graph.providers.keys.first()
      }
    }
    val filters = VcsLogFilterObject.collection(VcsLogFilterObject.fromRange("master", "feature"),
                                                VcsLogFilterObject.fromPaths(listOf(filePath)))

    val provider = object : TestVcsLogProvider() {
      override fun getCommitsMatchingFilter(root: VirtualFile,
                                            filterCollection: VcsLogFilterCollection,
                                            maxCount: Int): List<TimedVcsCommit> {
        return listOf(graph.allCommits.first { it.id == 2 }.toVcsCommit(graph.hashMap))
      }

      override fun resolveReference(ref: String, root: VirtualFile): Hash? {
        return when (ref) {
          "master" -> graph.getHash(1)
          "feature" -> graph.getHash(2)
          else -> null
        }
      }
    }
    graph.providers = graph.roots.associateWith { provider }

    val visiblePack = graph.build(filters)
    assertCommits(visiblePack.visibleGraph, 2)
  }

  @Test
  fun `filter by range where ref is unresolved in one of the roots`() {
    val root1 = MockVirtualFile("root1")
    val root2 = MockVirtualFile("root2")

    val graph = multiRootGraph {
      root(root1) {
        1(3) * "master"
        2(3) * "feature"
        3(4)
        4()
      }

      root(root2) {
        6(5) * "master"
        5()
      }
    }

    val filters = VcsLogFilterObject.collection(VcsLogFilterObject.fromRange("master", "feature"))
    val visiblePack = graph.build(filters)
    assertCommits(visiblePack.visibleGraph, 2)
  }

  @Test
  fun `filter by hash range in multi-root project`() {
    val root1 = MockVirtualFile("root1")
    val root2 = MockVirtualFile("root2")

    val graph = multiRootGraph {
      root(root1) {
        1(2) * "master"
        2(3)
        3()
      }

      root(root2) {
        5(4) * "master"
        4()
      }
    }

    val hash1 = graph.getHash(1)
    val hash2 = graph.getHash(2)
    val filters = VcsLogFilterObject.collection(VcsLogFilterObject.fromRange(hash2.asString(), hash1.asString()))
    val visiblePack = graph.build(filters)
    assertCommits(visiblePack.visibleGraph, 1)
  }

  @Test
  fun `filter by range where ref is unresolved`() {
    val graph = graph {
      1(3) *"master"
      2(3) *"feature"
      3(4)
      4()
    }
    val filters = VcsLogFilterObject.collection(VcsLogFilterObject.fromRange("master", "unknown"))

    val visiblePack = graph.build(filters)
    assertEquals(0, visiblePack.visibleGraph.visibleCommitCount, "Graph should be empty, but was: $graph")
  }

  private fun GraphCommit<Int>.toVcsCommit(storage: VcsLogStorage) = TimedVcsCommitImpl(storage.getCommitId(this.id)!!.hash, storage.getHashes(this.parents), 1)

  private fun assertDoesNotContain(graph: VisibleGraph<Int>, id: Int) {
    assertNull((1..graph.visibleCommitCount).firstOrNull { graph.getRowInfo(it - 1).commit == id })
  }

  private fun assertCommits(graph: VisibleGraph<Int>, vararg ids: Int) {
    assertEquals(ids.size, graph.visibleCommitCount, "Incorrect number of commits in graph: $graph. Expected: ${ids.asList()}")
    ids.forEachIndexed { index, id ->
      val actual = graph.getRowInfo(index).commit
      assertEquals(id, actual, "Unexpected commit $actual instead of $id at row $index")
    }
  }

  data class Ref(val name: String, val commit: Int)
  data class CommitMetaData(val user: VcsUser? = DEFAULT_USER, val subject: String = "default commit message")

  data class SingleRootGraph(val commits: List<GraphCommit<Int>>,
                             val refs: Set<Ref>,
                             val data: HashMap<GraphCommit<Int>, CommitMetaData>)

  inner class MultiRootGraph(private val graphsByRoots: Map<VirtualFile, SingleRootGraph>) {

    val roots = graphsByRoots.keys
    var providers: Map<VirtualFile, TestVcsLogProvider> = graphsByRoots.mapValues { TestVcsLogProvider() }

    val commits: Map<VirtualFile, List<GraphCommit<Int>>> = graphsByRoots.mapValues { it.value.commits }
    val allCommits = commits.values.flatten()
    val hashMap = ConstantVcsLogStorage(commits, graphsByRoots.mapValues { it.value.refs })

    fun build(filters: VcsLogFilterCollection): VisiblePack {

      val commits = graphsByRoots.values.map { it.commits }.flatten()

      val refs = hashMap.storagesByRoot.mapValues { (_, storage) -> CompressedRefs(HashSet(storage.refs.values), hashMap) }
      val dataPack = DataPack.build(commits, refs, providers, hashMap, true)

      val detailsCache = TopCommitsCache(hashMap)
      val details = graphsByRoots.map { (root, singleGraph) ->
        singleGraph.data.entries.mapNotNull {
          val hash = hashMap.getCommitId(it.key.id).hash
          if (it.value.user == null)
            null
          else VcsCommitMetadataImpl(hash, hashMap.getHashes(it.key.parents), 1L, root, it.value.subject,
                                     it.value.user!!, it.value.subject, it.value.user!!, 1L)
        }
      }.flatten()
      detailsCache.storeDetails(details)

      val builder = VcsLogFiltererImpl(providers, hashMap, detailsCache, newTrivialDataGetter(), EmptyIndex())

      return builder.filter(dataPack, VisiblePack.EMPTY, PermanentGraph.SortType.Normal, filters, CommitCountStage.INITIAL).first
    }

    private fun newTrivialDataGetter(): DataGetter<VcsFullCommitDetails> {
      return object : DataGetter<VcsFullCommitDetails> {
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
    }

    fun getHash(id: Int): Hash {
      return hashMap.getCommitId(id).hash
    }
  }

  fun VcsLogStorage.getHashes(ids: List<Int>) = ids.map { getCommitId(it)!!.hash }

  private fun graph(f: GraphBuilder.() -> Unit): MultiRootGraph {
    return multiRootGraph {
      root(MockVirtualFile("root")) {
        f()
      }
    }
  }

  private fun multiRootGraph(f: MultiRootGraphBuilder.() -> Unit): MultiRootGraph {
    val builder = MultiRootGraphBuilder()
    builder.f()
    return builder.done()
  }

  inner class MultiRootGraphBuilder {
    private val graphsByRoots = mutableMapOf<VirtualFile, SingleRootGraph>()

    fun root(root: VirtualFile, f: GraphBuilder.() -> Unit) {
      val builder = GraphBuilder()
      builder.f()
      val graph = builder.done()
      graphsByRoots[root] = graph
    }

    fun done() = MultiRootGraph(graphsByRoots)
  }

  inner class GraphBuilder {
    val commits = ArrayList<GraphCommit<Int>>()
    val refs = HashSet<Ref>()
    val data = HashMap<GraphCommit<Int>, CommitMetaData>()

    operator fun Int.invoke(vararg id: Int): GraphCommit<Int> {
      val commit = GraphCommitImpl.createCommit(this, id.toList(), this.toLong())
      commits.add(commit)
      data[commit] = CommitMetaData()
      return commit
    }

    operator fun GraphCommit<Int>.times(name: String): GraphCommit<Int> {
      refs.add(Ref(name, this.id))
      return this
    }

    operator fun GraphCommit<Int>.plus(name: String): GraphCommit<Int> {
      data[this] = CommitMetaData(VcsUserImpl(name, "$name@example.com"))
      return this
    }

    operator fun GraphCommit<Int>.plus(user: VcsUser?): GraphCommit<Int> {
      data[this] = CommitMetaData(user)
      return this
    }

    fun done() = SingleRootGraph(commits, refs, data)
  }

  class ConstantVcsLogStorage(private val commitsByRoot: Map<VirtualFile, List<GraphCommit<Int>>>,
                              private val refsByRoot: Map<VirtualFile, Set<Ref>>) : VcsLogStorage {

    val storagesByRoot = generate()

    data class SingleRootStorage(val hashes: Map<Int, Hash>, val refs: Map<Int, VcsRef>) {
      val hashesReversed = hashes.entries.map { Pair(it.value, it.key) }.toMap()
      val refsReversed = refs.entries.map { Pair(it.value, it.key) }.toMap()
    }

    private fun generate() :  Map<VirtualFile, SingleRootStorage> {
      var commitIndex = 1
      var refIndex = 1
      return commitsByRoot.mapValues { (root, commits) ->
        val hashes : Map<Int, Hash> = commits.map {
          val currentIndex = commitIndex
          commitIndex++
          val hash = generateHashForIndex(currentIndex)
          currentIndex to hash
        }.toMap()

        val refs:Map<Int, VcsRef> = refsByRoot.getValue(root).map { ref ->
          val vcsRef = VcsRefImpl(hashes.getValue(ref.commit), ref.name, BRANCH_TYPE, root)
          val currentIndex = refIndex
          refIndex++
          currentIndex to vcsRef
        }.toMap()

        SingleRootStorage(hashes, refs)
      }
    }

    private fun generateHashForIndex(currentIndex: Int): Hash {
      val hexIndex = currentIndex.toString(16)
      val remainingSize = FULL_HASH_LENGTH - hexIndex.length

      val sb = StringBuilder()
      for (i in 0 until remainingSize) {
        val randomHexChar = kotlin.random.Random.nextInt(0 until 16).toString(16)
        sb.append(randomHexChar)
      }

      val hashString = hexIndex + sb.toString()
      assertEquals(FULL_HASH_LENGTH, hashString.length, "Hash generated incorrectly: [$hashString]")
      return HashImpl.build(hashString)
    }

    override fun getCommitIndex(hash: Hash, root: VirtualFile) = storagesByRoot.getValue(root).hashesReversed.getValue(hash)

    override fun getCommitId(commitIndex: Int): CommitId {
      return storagesByRoot.entries.mapNotNull { (root, storage) ->
        val hash = storage.hashes[commitIndex]
        if (hash != null) CommitId(hash, root) else null
      }.first()
    }

    override fun containsCommit(id: CommitId): Boolean {
      return storagesByRoot.any { (root, storage) -> storage.hashes.any { it.value == id.hash  && root == id.root} }
    }

    override fun getVcsRef(refIndex: Int): VcsRef {
      return storagesByRoot.values.mapNotNull { storage -> storage.refs[refIndex] }.first()
    }

    override fun getRefIndex(ref: VcsRef): Int = storagesByRoot.getValue(ref.root).refsReversed.getValue(ref)

    override fun iterateCommits(consumer: Function<in CommitId, Boolean>) {
      storagesByRoot.entries.forEach { (root, storage) ->
        storage.hashes.values.forEach {
          val stop = consumer.`fun`(CommitId(it, root))
          if (stop) return
        }
      }
    }

    override fun flush() {
    }
  }
}
