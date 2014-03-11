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
import com.intellij.vcs.log.newgraph.AbstractTestWithTextFile;
import com.intellij.vcs.log.newgraph.GraphFlags;
import com.intellij.vcs.log.newgraph.PermanentGraph;
import com.intellij.vcs.log.newgraph.facade.ContainingBranchesGetter;
import com.intellij.vcs.log.newgraph.utils.DfsUtil;
import com.intellij.vcs.log.parser.SimpleCommitListParser;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.vcs.log.newgraph.GraphStrUtils.containingBranchesGetterToStr;
import static org.junit.Assert.assertEquals;

public class ContainingBranchesTest extends AbstractTestWithTextFile {
  private final static String SEPARATOR = "\nBRANCH NODES:\n";

  public ContainingBranchesTest() {
    super("containingBranches/");
  }

  private static Set<Integer> parseBranchNodeIndex(String str) {
    Set<Integer> result = new HashSet<Integer>();
    for (String subStr : str.split("\\s")) {
      result.add(Integer.parseInt(subStr));
    }
    return result;
  }


  @Override
  protected void runTest(String in, String out) {
    int i = in.indexOf(SEPARATOR);
    List<GraphCommit> commits = SimpleCommitListParser.parseCommitList(in.substring(0, i));

    GraphFlags flags = new GraphFlags(commits.size());
    PermanentGraph graph = PermanentGraphBuilder.build(flags.getSimpleNodeFlags(), commits).first;
    ContainingBranchesGetter containingBranchesGetter = new ContainingBranchesGetter(graph,
                                                                                     parseBranchNodeIndex(in.substring(i + SEPARATOR.length())),
                                                                                     new DfsUtil(1000),
                                                                                     new GraphFlags(graph.nodesCount()).getTempFlags());

    assertEquals(out, containingBranchesGetterToStr(containingBranchesGetter, graph.nodesCount()));
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
