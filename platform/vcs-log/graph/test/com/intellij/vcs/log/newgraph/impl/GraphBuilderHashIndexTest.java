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
import com.intellij.vcs.log.newgraph.AbstractTestWithTextFile;
import com.intellij.vcs.log.newgraph.PermanentGraph;
import com.intellij.vcs.log.parser.SimpleCommitListParser;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static com.intellij.vcs.log.newgraph.GraphStrUtils.permanentGraphToHashIndex;
import static org.junit.Assert.assertEquals;

public class GraphBuilderHashIndexTest extends AbstractTestWithTextFile {

  public GraphBuilderHashIndexTest() {
    super("graphHashIndex/");
  }

  @Override
  protected void runTest(String in, String out) {
    List<GraphCommit> commits = SimpleCommitListParser.parseCommitList(in);
    PermanentGraph graph = PermanentGraphBuilder.build(commits).first;

    assertEquals(out, permanentGraphToHashIndex(graph));
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

}
