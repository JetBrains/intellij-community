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

package com.intellij.vcs.log.newgraph.impl;

import com.intellij.vcs.log.GraphCommit;
import com.intellij.vcs.log.facade.graph.permanent.PermanentGraphBuilder;
import com.intellij.vcs.log.facade.graph.permanent.PermanentGraphLayoutBuilder;
import com.intellij.vcs.log.newgraph.AbstractTestWithTextFile;
import com.intellij.vcs.log.newgraph.PermanentGraph;
import com.intellij.vcs.log.newgraph.PermanentGraphLayout;
import com.intellij.vcs.log.newgraph.utils.DfsUtil;
import com.intellij.vcs.log.parser.SimpleCommitListParser;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import static com.intellij.vcs.log.newgraph.GraphStrUtils.permanentGraphLayoutModelToStr;
import static org.junit.Assert.assertEquals;

public class GraphLayoutBuilderTest extends AbstractTestWithTextFile {
  public GraphLayoutBuilderTest() {
    super("layoutBuilder/");
  }

  @Override
  protected void runTest(String in, String out) {
    List<GraphCommit> commits = SimpleCommitListParser.parseCommitList(in);
    final PermanentGraph graph = PermanentGraphBuilder.build(commits).first;

    DfsUtil dfsUtil = new DfsUtil(commits.size());
    PermanentGraphLayout graphLayout = PermanentGraphLayoutBuilder.build(dfsUtil, graph, new Comparator<Integer>() {
      @Override
      public int compare(@NotNull Integer o1, @NotNull Integer o2) {
        Integer hashIndex1 = graph.getHashIndex(o1);
        return hashIndex1.compareTo(graph.getHashIndex(o2));
      }
    });

    assertEquals(out, permanentGraphLayoutModelToStr(graphLayout, graph.nodesCount()));
  }

  @Test
  public void manyNodes() throws IOException {
    doTest("manyNodes");
  }

  @Test
  public void notFullGraph() throws IOException {
    doTest("notFullGraph");
  }

  @Test
  public void oneNode() throws IOException {
    doTest("oneNode");
  }

  @Test
  public void oneNodeNotFullGraph() throws IOException {
    doTest("oneNodeNotFullGraph");
  }

  @Test
  public void headsOrder() throws IOException {
    doTest("headsOrder");
  }
}
