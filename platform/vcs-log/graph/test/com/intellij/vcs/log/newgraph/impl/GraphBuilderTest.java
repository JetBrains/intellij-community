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

import com.intellij.openapi.util.Pair;
import com.intellij.vcs.log.GraphCommit;
import com.intellij.vcs.log.facade.graph.permanent.PermanentGraphBuilder;
import com.intellij.vcs.log.facade.graph.permanent.PermanentGraphImpl;
import com.intellij.vcs.log.newgraph.AbstractTestWithTextFile;
import com.intellij.vcs.log.newgraph.PermanentGraph;
import com.intellij.vcs.log.parser.SimpleCommitListParser;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.intellij.vcs.log.newgraph.GraphStrUtils.commitsWithNotLoadParentMapToStr;
import static com.intellij.vcs.log.newgraph.GraphStrUtils.permanentGraphTorStr;
import static org.junit.Assert.assertEquals;

public class GraphBuilderTest extends AbstractTestWithTextFile {
  public GraphBuilderTest() {
    super("graphBuilder/");
  }

  @Override
  protected void runTest(String in, String out) {
    List<GraphCommit> commits = SimpleCommitListParser.parseCommitList(in);
    Pair<PermanentGraphImpl,Map<Integer,GraphCommit>> graphAndCommitsWithNotLoadParent = PermanentGraphBuilder
      .build(commits);
    Map<Integer, GraphCommit> commitsWithNotLoadParent = graphAndCommitsWithNotLoadParent.second;
    PermanentGraph graph = graphAndCommitsWithNotLoadParent.first;

    String actual = permanentGraphTorStr(graph);
    if (!commitsWithNotLoadParent.isEmpty()) {
      actual += "\nNOT LOAD MAP:\n";
      actual += commitsWithNotLoadParentMapToStr(commitsWithNotLoadParent);
    }
    assertEquals(out, actual);
  }



  @Test
  public void simple() throws IOException {
    doTest("simple");
  }

  @Test
  public void manyNodes() throws IOException {
    doTest("manyNodes");
  }

  @Test
  public void manyUpNodes() throws IOException {
    doTest("manyUpNodes");
  }

  @Test
  public void manyDownNodes() throws IOException {
    doTest("manyDownNodes");
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
  public void notFullGraph() throws IOException {
    doTest("notFullGraph");
  }

  @Test
  public void parentsOrder() throws IOException {
    doTest("parentsOrder");
  }

  @Test
  public void duplicateParents() throws IOException {
    doTest("duplicateParents");
  }
}
