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
import com.intellij.vcs.log.graph.api.permanent.PermanentCommitsInfo;
import com.intellij.vcs.log.graph.impl.CommitIdManager;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.intellij.vcs.log.graph.GraphStrUtils.commitsInfoToStr;
import static org.junit.Assert.assertEquals;

public abstract class GraphBuilderHashIndexTest<CommitId> extends AbstractTestWithTwoTextFile {

  public GraphBuilderHashIndexTest() {
    super("graphHashIndex/");
  }

  @Override
  protected void runTest(String in, String out) {
    final List<GraphCommit<CommitId>> commits = getCommitIdManager().parseCommitList(in);
    PermanentCommitsInfo<CommitId> commitsInfo = PermanentCommitsInfoImpl.newInstance(commits, Collections.<Integer, CommitId>emptyMap());

    assertEquals(out, commitsInfoToStr(commitsInfo, commits.size(), getCommitIdManager().getToStrFunction()));
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

  public static class StringTest extends GraphBuilderHashIndexTest<String> {
    @Override
    protected CommitIdManager<String> getCommitIdManager() {
      return CommitIdManager.STRING_COMMIT_ID_MANAGER;
    }
  }

  public static class IntegerTest extends GraphBuilderHashIndexTest<Integer> {
    @Override
    protected CommitIdManager<Integer> getCommitIdManager() {
      return CommitIdManager.INTEGER_COMMIT_ID_MANAGER;
    }
  }
}
