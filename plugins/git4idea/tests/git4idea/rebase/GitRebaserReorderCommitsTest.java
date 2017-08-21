/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.rebase;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.Git;
import git4idea.test.GitSingleRepoTest;
import git4idea.test.GitTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.BuiltInServerManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.vcs.Executor.touch;
import static git4idea.test.GitExecutor.*;

/**
 * NB: we don't test merge commits here, since {@link GitRebaser#reoderCommitsIfNeeded(VirtualFile, String, List)}
 * doesn't handle it for now.
 */
public class GitRebaserReorderCommitsTest extends GitSingleRepoTest {

  private GitRebaser myRebaser;
  private String myFirstCommit;

  @Override protected void setUp() throws Exception {
    super.setUp();
    GitTestUtil.createRepository(myProject, myProjectPath, false);
    myRebaser = new GitRebaser(myProject, Git.getInstance(), new EmptyProgressIndicator());
    myFirstCommit = makeCommit();
    BuiltInServerManager.getInstance().waitForStart();
  }

  @Override
  protected boolean makeInitialCommit() {
    return false;
  }

  public void testReorderingNothingShouldDoNothing() throws Exception {
    myRebaser.reoderCommitsIfNeeded(myProjectRoot, myFirstCommit, Collections.emptyList());
    assertCommits(myFirstCommit);
  }

  public void testReorderingOneShouldDoNothing() throws Exception {
    String hash = makeCommit();
    myRebaser.reoderCommitsIfNeeded(myProjectRoot, myFirstCommit, Collections.singletonList(hash));
    assertCommits(myFirstCommit, hash);
  }

  public void testReorderingAllShouldDoNothing() throws Exception {
    String hash1 = makeCommit();
    String hash2 = makeCommit();
    myRebaser.reoderCommitsIfNeeded(myProjectRoot, myFirstCommit, Arrays.asList(hash1, hash2));
    assertCommits(myFirstCommit, hash1, hash2);
  }

  public void disabled_testReorderingOldestShouldDoNothing() throws Exception {
    String[] hashes = makeCommits(3);
    myRebaser.reoderCommitsIfNeeded(myProjectRoot, myFirstCommit, Arrays.asList(hashes[0], hashes[1]));
    assertCommits(myFirstCommit, hashes[0], hashes[1], hashes[2]);
  }

  public void disabled_testReorderingOneCommit() throws Exception {
    String[] hashes = makeCommits(3);
    myRebaser.reoderCommitsIfNeeded(myProjectRoot, myFirstCommit, Collections.singletonList(hashes[2]));
    assertCommits(myFirstCommit, hashes[2], hashes[0], hashes[1]);
  }

  public void disabled_testReorderingTwoCommits() throws Exception {
    String[] hashes = makeCommits(3);
    myRebaser.reoderCommitsIfNeeded(myProjectRoot, myFirstCommit, Arrays.asList(hashes[2], hashes[1]));
    assertCommits(myFirstCommit, hashes[2], hashes[1], hashes[0]);
  }

  private static String[] makeCommits(int number) {
    String[] hashes = new String[number];
    for (int i = 0; i < hashes.length; i++) {
      hashes[i] = makeCommit();
    }
    return hashes;
  }

  @NotNull
  private static String makeCommit() {
    touch(Math.random() + ".txt", "initial" + Math.random());
    addCommit("some commit");
    return last();
  }

  private static void assertCommits(String... commits) {
    final String[] hashes = git("rev-list --reverse HEAD").split("\n");
    assertEquals(commits.length, hashes.length);
    for (int i = 0; i < commits.length; i++) {
      assertEquals("Commit #" + i + " doesn't  match", commits[i], hashes[i]);
    }
  }

}
