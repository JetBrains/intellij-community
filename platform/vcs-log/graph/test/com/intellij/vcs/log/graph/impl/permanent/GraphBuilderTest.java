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

import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.impl.CommitIdManager;
import com.intellij.vcs.log.graph.AbstractTestWithTextFile;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.intellij.vcs.log.graph.GraphStrUtils.commitsWithNotLoadParentMapToStr;
import static com.intellij.vcs.log.graph.GraphStrUtils.linearGraphToStr;
import static org.junit.Assert.assertEquals;

public abstract class GraphBuilderTest<CommitId extends Comparable<CommitId>> extends AbstractTestWithTextFile {
  public GraphBuilderTest() {
    super("graphBuilder/");
  }

  @Override
  protected void runTest(String in, String out) {
    List<GraphCommit<CommitId>> commits = getCommitIdManager().parseCommitList(in);

    PermanentLinearGraphBuilder<CommitId> graphBuilder = PermanentLinearGraphBuilder.newInstance(commits);
    PermanentLinearGraphImpl graph = graphBuilder.build();

    String actual = linearGraphToStr(graph);
    Map<CommitId, GraphCommit<CommitId>> commitsWithNotLoadParent = graphBuilder.getCommitsWithNotLoadParent();
    if (!commitsWithNotLoadParent.isEmpty()) {
      actual += "\nNOT LOAD MAP:\n";
      actual += commitsWithNotLoadParentMapToStr(commitsWithNotLoadParent, getCommitIdManager().getToStrFunction());
    }
    assertEquals(out, actual);
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

  public static class StringTest extends GraphBuilderTest<String> {
    @Override
    protected CommitIdManager<String> getCommitIdManager() {
      return CommitIdManager.STRING_COMMIT_ID_MANAGER;
    }
  }

  public static class IntegerTest extends GraphBuilderTest<Integer> {
    @Override
    protected CommitIdManager<Integer> getCommitIdManager() {
      return CommitIdManager.INTEGER_COMMIT_ID_MANAGER;
    }
  }
}
