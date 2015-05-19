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

import com.intellij.execution.process.ProcessOutput;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.zmlx.hg4idea.execution.HgCommandResult;

import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class HgPushParseTest {

  @NotNull private final String myOutput;
  private final int myExpected;

  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors", "UnusedParameters"})
  public HgPushParseTest(@NotNull String name, @NotNull String output, int expected) {
    myOutput = output;
    myExpected = expected;
  }

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> createData() {
    return ContainerUtil.newArrayList(new Object[][]{
      {"DEFAULT_1", "pushing to /Users/user/TTT/AHG\n" +
                    "searching for changes\n" +
                    "adding changesets\n" +
                    "adding manifests\n" +
                    "adding file changes\n" +
                    "added 1 changesets with 1 changes to 1 files", 1},
      {"DEFAULT_2", "pushing to /Users/user/TTT/AHG\n" +
                    "searching for changes\n" +
                    "adding changesets\n" +
                    "adding manifests\n" +
                    "adding file changes\n" +
                    "added 2 changesets with 3 changes to 1 files", 2},
      {"EXTENSION_KILN_ONE", "hg push http://<my repo>\n" +
                             "pushing to http://<my repo>\n" +
                             "searching for changes\n" +
                             "              \"remote: kiln: successfully pushed one changeset", 1},
      {"EXTENSION_KILN_4", "hg push http://<my repo>\n" +
                           "pushing to http://<my repo>\n" +
                           "searching for changes\n" +
                           "remote: kiln: successfully pushed 4 changesets", 4}
    });
  }

  @Test
  public void testValid() {
    ProcessOutput processOutput = new ProcessOutput(0);
    processOutput.appendStdout(myOutput);
    assertEquals(" Wrong commits number for " + myOutput, myExpected,
                 HgPusher.getNumberOfPushedCommits(new HgCommandResult(processOutput)));
  }
}
