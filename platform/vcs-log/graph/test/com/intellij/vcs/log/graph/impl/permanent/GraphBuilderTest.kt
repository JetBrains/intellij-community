/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.vcs.log.graph.impl.permanent

import com.intellij.vcs.log.graph.AbstractTestWithTwoTextFile
import com.intellij.vcs.log.graph.asString
import com.intellij.vcs.log.graph.impl.CommitIdManager
import org.junit.Assert.assertEquals
import org.junit.Test

abstract class GraphBuilderTest<CommitId : Comparable<CommitId>> : AbstractTestWithTwoTextFile("graphBuilder/") {

  override fun runTest(`in`: String, out: String) {
    val commits = getCommitIdManager().parseCommitList(`in`)

    val graphBuilder = PermanentLinearGraphBuilder.newInstance<CommitId>(commits)
    val graph = graphBuilder.build { dom -> -getCommitIdManager().toInt(dom) }

    val actual = graph.asString(false)
    assertEquals(out, actual)
  }

  protected abstract fun getCommitIdManager(): CommitIdManager<CommitId>

  @Test
  fun simple() {
    doTest("simple")
  }

  @Test
  fun manyNodes() {
    doTest("manyNodes")
  }

  @Test
  fun manyUpNodes() {
    doTest("manyUpNodes")
  }

  @Test
  fun manyDownNodes() {
    doTest("manyDownNodes")
  }

  @Test
  fun oneNode() {
    doTest("oneNode")
  }

  @Test
  fun oneNodeNotFullGraph() {
    doTest("oneNodeNotFullGraph")
  }

  @Test
  fun notFullGraph() {
    doTest("notFullGraph")
  }

  @Test
  fun parentsOrder() {
    doTest("parentsOrder")
  }

  @Test
  fun duplicateParents() {
    doTest("duplicateParents")
  }

  class StringTest : GraphBuilderTest<String>() {
    override fun getCommitIdManager(): CommitIdManager<String> {
      return CommitIdManager.STRING_COMMIT_ID_MANAGER
    }
  }

  class IntegerTest : GraphBuilderTest<Int>() {
    override fun getCommitIdManager(): CommitIdManager<Int> {
      return CommitIdManager.INTEGER_COMMIT_ID_MANAGER
    }
  }
}
