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
package git4idea.push;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class GitPushNativeResultParserTest {

  public static final String STANDARD_PREFIX = "Counting objects: 20, done.\n" +
                                               "Delta compression using up to 8 threads.\n" +
                                               "Compressing objects: 100% (14/14), done.\n" +
                                               "Writing objects: 100% (20/20), 1.70 KiB | 0 bytes/s, done.\n" +
                                               "Total 20 (delta 7), reused 0 (delta 0)\n" +
                                               "To /Users/loki/sandbox/git/parent.git";
  public static final String TARGET_PREFIX = "To http://example.com/git/parent.git";
  public static final String PUSH_DEFAULT_WARNING = "warning: push.default is unset; its implicit value has changed in\n" +
                                                    "Git 2.0 from 'matching' to 'simple'. To squelch this message\n" +
                                                    "and maintain the traditional behavior, use:\n" +
                                                    "\n" +
                                                    "  git config --global push.default matching\n" +
                                                    "\n" +
                                                    "To squelch this message and adopt the new behavior now, use:\n" +
                                                    "\n" +
                                                    "  git config --global push.default simple\n" +
                                                    "\n" +
                                                    "When push.default is set to 'matching', git will push local branches\n" +
                                                    "to the remote branches that already exist with the same name.\n" +
                                                    "\n" +
                                                    "Since Git 2.0, Git defaults to the more conservative 'simple'\n" +
                                                    "behavior, which only pushes the current branch to the corresponding\n" +
                                                    "remote branch that 'git pull' uses to update the current branch.\n" +
                                                    "\n" +
                                                    "See 'git help config' and search for 'push.default' for further information.\n" +
                                                    "(the 'simple' mode was introduced in Git 1.7.11. Use the similar mode\n" +
                                                    "'current' instead of 'simple' if you sometimes use older versions of Git)";
  public static final String SUCCESS_SUFFIX = "Done";
  public static final String REJECT_SUFFIX = "Done\n" +
                                             "error: failed to push some refs to '/Users/loki/sandbox/git/parent.git'\n" +
                                             "hint: Updates were rejected because the tip of your current branch is behind\n" +
                                             "hint: its remote counterpart. Integrate the remote changes (e.g.\n" +
                                             "hint: 'git pull ...') before pushing again.\n" +
                                             "hint: See the 'Note about fast-forwards' in 'git push --help' for details.";

  @Test
  public void success() {
    String output = " \trefs/heads/master:refs/heads/master\t3e62822..a537351";
    GitPushNativeResult result = GitPushNativeResultParser.parse(Arrays.asList(STANDARD_PREFIX, TARGET_PREFIX, output, SUCCESS_SUFFIX));
    assertResult(GitPushNativeResult.Type.SUCCESS, "3e62822..a537351", result);
  }

  @Test
  public void rejected() {
    String output = "!\trefs/heads/master:refs/heads/master\t[rejected] (non-fast-forward)";
    GitPushNativeResult result = GitPushNativeResultParser.parse(Arrays.asList(STANDARD_PREFIX, TARGET_PREFIX, output, REJECT_SUFFIX));
    assertResult(GitPushNativeResult.Type.REJECTED, null, result);
  }

  @Test
  public void forcedUpdate() {
    String output = "+\trefs/heads/master:refs/heads/master\tb9b3235...23760f8 (forced update)";
    GitPushNativeResult result = GitPushNativeResultParser.parse(Arrays.asList(PUSH_DEFAULT_WARNING, TARGET_PREFIX, output));
    assertResult(GitPushNativeResult.Type.FORCED_UPDATE, "b9b3235...23760f8", result);
  }

  @Test
  public void upToDate() {
    String output = "=\trefs/heads/master:refs/heads/master\t[up to date]";
    GitPushNativeResult result = GitPushNativeResultParser.parse(Arrays.asList(TARGET_PREFIX, output));
    assertResult(GitPushNativeResult.Type.UP_TO_DATE, null, result);
  }
  
  @Test
  public void newRef() {
    String output = "*\trefs/heads/feature:refs/heads/feature2\t[new branch]";
    GitPushNativeResult result = GitPushNativeResultParser.parse(Arrays.asList(STANDARD_PREFIX, TARGET_PREFIX, output));
    assertResult(GitPushNativeResult.Type.NEW_REF, null, result);
  }

  private static void assertResult(GitPushNativeResult.Type expectedType, String expectedRange, GitPushNativeResult actualResult) {
    assertEquals(expectedType, actualResult.getType());
    assertEquals(expectedRange, actualResult.getRange());
  }

}