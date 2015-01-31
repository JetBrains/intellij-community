/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.vcs.log.data

import org.junit.Test
import com.intellij.mock.MockVirtualFile
import com.intellij.vcs.log.impl.TestVcsLogProvider
import com.intellij.vcs.log.VcsLogHashMap
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.Disposable
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl
import com.intellij.vcs.log.VcsRefType
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import java.awt.Color
import java.util.HashMap
import com.intellij.vcs.log.graph.GraphCommit
import java.util.ArrayList
import com.intellij.vcs.log.graph.GraphCommitImpl
import com.intellij.vcs.log.VcsRef
import java.util.HashSet
import com.intellij.vcs.log.impl.VcsRefImpl
import com.intellij.vcs.log.VcsLogFilterCollection
import kotlin.test.assertEquals
import com.intellij.vcs.log.VcsLogUserFilter
import com.intellij.vcs.log.VcsLogBranchFilter

class VisiblePackBuilderTest {

  Test fun no_filters() {
    val graph = graph {
      1(2) +"master"
      2(3)
      3(4)
      4()
    }
    val visiblePack = graph.build(filters(), CommitCountStage.INITIAL)
    assertEquals(4, visiblePack.getVisibleGraph().getVisibleCommitCount())
  }

  Test fun branch_filter() {
    val graph = graph {
      1(3) +"master"
      2(3) +"feature"
      3(4)
      4()
    }
    val visiblePack = graph.build(filters(branch = listOf("master")), CommitCountStage.INITIAL)
    assertEquals(3, visiblePack.getVisibleGraph().getVisibleCommitCount())
  }

  data class Ref(val name : String, val commit : Int)

  class Graph(val commits : List<GraphCommit<Int>>, val refs : Set<Ref>) {
    val root = MockVirtualFile("root") : VirtualFile
    val providers: Map<VirtualFile, TestVcsLogProvider> = mapOf(root to TestVcsLogProvider(root))

    fun build(filters : VcsLogFilterCollection, commitCountStage : CommitCountStage) : VisiblePack {
      val hashMap = generateHashMap(commits.maxBy { it.getId() }!!.getId())
      val cdg = CommitDetailsGetter(hashMap, providers, TRIVIAL_DISPOSABLE)

      val refs = refs.mapTo(HashSet<VcsRef>(), {
        VcsRefImpl(hashMap.getHash(it.commit), it.name, BRANCH_TYPE, root)
      })

      val dataPack = DataPack.build(commits, mapOf(root to refs), providers, hashMap, true)
      val builder = VisiblePackBuilder(providers, hashMap, mapOf(), cdg)

      return builder.build(dataPack, PermanentGraph.SortType.Normal, filters, commitCountStage).first
    }

    fun generateHashMap(num: Int): VcsLogHashMap {
      val map = HashMap<Hash, Int>()
      for (i in 1..num) {
        map.put(HashImpl.build(i.toString()), i)
      }
      return ConstantVcsLogHashMap(map)
    }
  }

  fun filters(branch: List<String>? = null, user: VcsLogUserFilter? = null) = VcsLogFilterCollectionImpl(VcsLogBranchFilterImpl(branch), user, null, null, null, null, null)

  fun graph(f: GraphBuilder.() -> Unit) : Graph {
    val builder = GraphBuilder()
    builder.f()
    return builder.done()
  }

  class GraphBuilder {
    val commits = ArrayList<GraphCommit<Int>>()
    val refs = HashSet<Ref>()

    fun Int.invoke(vararg id: Int) : GraphCommit<Int> {
      val commit = GraphCommitImpl(this, id.toList(), this.toLong())
      commits.add(commit)
      return commit
    }

    fun GraphCommit<Int>.plus(name: String) = refs.add(Ref(name, this.getId()))

    fun done() = Graph(commits, refs)
  }

  class ConstantVcsLogHashMap(val map: Map<Hash, Int>) : VcsLogHashMap {
    val reverseMap = map.entrySet().map { Pair(it.value, it.key) }.toMap()

    override fun getCommitIndex(hash: Hash) = map.get(hash)!!

    override fun getHash(commitIndex: Int) = reverseMap.get(commitIndex)!!

    override fun findHashByString(string: String) = throw UnsupportedOperationException()
  }

  object BRANCH_TYPE : VcsRefType {
    override fun isBranch(): Boolean {
      return true;
    }

    override fun getBackgroundColor(): Color {
      throw UnsupportedOperationException()
    }
  }

  object TRIVIAL_DISPOSABLE : Disposable {
    override fun dispose() {
      throw UnsupportedOperationException()
    }
  }

}

