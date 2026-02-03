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

package com.intellij.vcs.log.graph.impl.permanent;

import com.intellij.vcs.log.graph.AbstractTestWithTwoTextFile;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.impl.CommitIdManager;
import com.intellij.vcs.log.graph.impl.facade.ReachableNodes;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.vcs.log.graph.GraphStrUtils.containingBranchesGetterToStr;
import static org.junit.Assert.assertEquals;

public abstract class ContainingBranchesTest<CommitId> extends AbstractTestWithTwoTextFile {
  private final static String SEPARATOR = "\nBRANCH NODES:\n";

  public ContainingBranchesTest() {
    super("containingBranches/");
  }

  private static Set<Integer> parseBranchNodeIndex(String str) {
    Set<Integer> result = new HashSet<>();
    for (String subStr : str.split("\\s")) {
      result.add(Integer.parseInt(subStr));
    }
    return result;
  }

  @Override
  protected void runTest(String in, String out) {
    int i = in.indexOf(SEPARATOR);
    List<GraphCommit<CommitId>> commits = getCommitIdManager().parseCommitList(in.substring(0, i));

    LinearGraph graph = PermanentLinearGraphBuilder.newInstance(commits).build();
    Set<Integer> branches = parseBranchNodeIndex(in.substring(i + SEPARATOR.length()));
    ReachableNodes reachableNodes =
      new ReachableNodes(LinearGraphUtils.asLiteLinearGraph(graph));

    assertEquals(out, containingBranchesGetterToStr(reachableNodes, branches, graph.nodesCount()));
  }

  protected abstract CommitIdManager<CommitId> getCommitIdManager();

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

  public static class StringTest extends ContainingBranchesTest<String> {
    @Override
    protected CommitIdManager<String> getCommitIdManager() {
      return CommitIdManager.STRING_COMMIT_ID_MANAGER;
    }
  }

  public static class IntegerTest extends ContainingBranchesTest<Integer> {
    @Override
    protected CommitIdManager<Integer> getCommitIdManager() {
      return CommitIdManager.INTEGER_COMMIT_ID_MANAGER;
    }
  }
}
