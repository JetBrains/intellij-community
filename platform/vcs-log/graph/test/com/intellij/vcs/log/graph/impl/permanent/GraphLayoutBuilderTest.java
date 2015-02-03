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
import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.impl.CommitIdManager;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import static com.intellij.vcs.log.graph.GraphStrUtils.permanentGraphLayoutModelToStr;
import static org.junit.Assert.assertEquals;

public abstract class GraphLayoutBuilderTest<CommitId> extends AbstractTestWithTwoTextFile {
  public GraphLayoutBuilderTest() {
    super("layoutBuilder/");
  }

  @Override
  protected void runTest(String in, String out) {
    final CommitIdManager<CommitId> idManager = getCommitIdManager();
    final List<GraphCommit<CommitId>> commits = idManager.parseCommitList(in);

    PermanentLinearGraphBuilder<CommitId> graphBuilder = PermanentLinearGraphBuilder.newInstance(commits);
    PermanentLinearGraphImpl graph = graphBuilder.build();

    GraphLayout graphLayout = GraphLayoutBuilder.build(graph, new Comparator<Integer>() {
      @Override
      public int compare(@NotNull Integer o1, @NotNull Integer o2) {
        CommitId id1 = commits.get(o1).getId();
        CommitId id2 = commits.get(o2).getId();
        return idManager.toStr(id1).compareTo(idManager.toStr(id2));
      }
    });
    assertEquals(out, permanentGraphLayoutModelToStr(graphLayout, graph.nodesCount()));
  }

  protected abstract CommitIdManager<CommitId> getCommitIdManager();

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

  public static class StringTest extends GraphLayoutBuilderTest<String> {
    @Override
    protected CommitIdManager<String> getCommitIdManager() {
      return CommitIdManager.STRING_COMMIT_ID_MANAGER;
    }
  }

  public static class IntegerTest extends GraphLayoutBuilderTest<Integer> {
    @Override
    protected CommitIdManager<Integer> getCommitIdManager() {
      return CommitIdManager.INTEGER_COMMIT_ID_MANAGER;
    }
  }

}
