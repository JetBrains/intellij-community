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
package org.zmlx.hg4idea.push;

import hg4idea.test.HgPlatformTest;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.command.HgPushCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgCommandResultHandler;

import java.io.StringWriter;

import static com.intellij.openapi.vcs.Executor.*;
import static hg4idea.test.HgExecutor.hg;

public class HgPushTest extends HgPlatformTest {
  private static String aFile = "A.txt";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    prepareSecondRepository();
  }

  public void testOneCommitPushSuccessful() {
    createChanges();
    HgPushCommand pushCommand = new HgPushCommand(myProject, myChildRepo, myRepository.getPath());
    pushCommand.execute(new HgCommandResultHandler() {
      @Override
      public void process(@Nullable HgCommandResult result) {
        if (result == null) {
          return;
        }
        assertTrue(result.getExitValue() == HgPusher.PUSH_SUCCEEDED_EXIT_VALUE);
        assertTrue(HgPusher.getNumberOfPushedCommits(result) == 1);
      }
    });
  }

  public void testSeveralCommitPushSuccessful() {
    createChanges();
    echo(aFile, "modified");
    hg("commit -m 'modified file'");
    HgPushCommand pushCommand = new HgPushCommand(myProject, myChildRepo, myRepository.getPath());
    pushCommand.execute(new HgCommandResultHandler() {
      @Override
      public void process(@Nullable HgCommandResult result) {
        if (result == null) {
          return;
        }
        assertTrue(result.getExitValue() == HgPusher.PUSH_SUCCEEDED_EXIT_VALUE);
        assertTrue(HgPusher.getNumberOfPushedCommits(result) == 2);
      }
    });
  }

  public void testPushSuccessfulOneCommitMessageParsing() {
    StringWriter out = new StringWriter();
    out.write("hg push http://<my repo URL>\n" +
              "pushing to http://<my repo URL>\n" +
              "searching for changes\n" +
              "remote: kiln: successfully pushed one changeset");
    HgCommandResult testResult = new HgCommandResult(out, new StringWriter(), 0);
    assertTrue(HgPusher.getNumberOfPushedCommits(testResult) == 1);
  }

  public void testPushSuccessfulSeveralCommitMessageParsing() {
    StringWriter out = new StringWriter();
    out.write("hg push http://<my repo>\n" +
              "pushing to http://<my repo>\n" +
              "searching for changes\n" +
              "remote: kiln: successfully pushed 4 changesets");
    HgCommandResult testResult = new HgCommandResult(out, new StringWriter(), 0);
    assertTrue(HgPusher.getNumberOfPushedCommits(testResult) == 4);
  }

  private void createChanges() {
    cd(myChildRepo);
    touch(aFile, "base");
    hg("add " + aFile);
    hg("commit -m 'create file'");
  }
}
