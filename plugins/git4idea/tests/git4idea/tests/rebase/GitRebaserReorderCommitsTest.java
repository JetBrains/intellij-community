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
package git4idea.tests.rebase;

import com.intellij.openapi.vfs.VirtualFile;
import git4idea.rebase.GitRebaser;
import git4idea.tests.GitSingleUserTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.testng.Assert.assertEquals;

/**
 * NB: we don't test merge commits here, since {@link GitRebaser#reoderCommitsIfNeeded(com.intellij.openapi.vfs.VirtualFile, String, java.util.List)}
 * is not suitable for this.
 * @author Kirill Likhodedov
 */
public class GitRebaserReorderCommitsTest extends GitSingleUserTest {

  private GitRebaser myRebaser;
  private VirtualFile myRoot;
  private String myFirstCommit;

  @BeforeMethod @Override protected void setUp() throws Exception {
    super.setUp();
    myRebaser = new GitRebaser(myProject, null);
    myRoot = myRepo.getDir();
    myFirstCommit = makeCommit();
  }

  @Test
  public void reorderingNothingShouldDoNothing() throws Exception {
    myRebaser.reoderCommitsIfNeeded(myRoot, myFirstCommit, Collections.<String>emptyList());
    assertCommits(myFirstCommit);
  }

  @Test
  public void reorderingOneShouldDoNothing() throws Exception {
    String hash = makeCommit();
    myRebaser.reoderCommitsIfNeeded(myRoot, myFirstCommit, Collections.singletonList(hash));
    assertCommits(myFirstCommit, hash);
  }

  @Test
  public void reorderingAllShouldDoNothing() throws Exception {
    String hash1 = makeCommit();
    String hash2 = makeCommit();
    myRebaser.reoderCommitsIfNeeded(myRoot, myFirstCommit, Arrays.asList(hash1, hash2));
    assertCommits(myFirstCommit, hash1, hash2);
  }

  @Test
  public void reorderingOldestShouldDoNothing() throws Exception {
    String[] hashes = makeCommits(3);
    myRebaser.reoderCommitsIfNeeded(myRoot, myFirstCommit, Arrays.asList(hashes[0], hashes[1]));
    assertCommits(myFirstCommit, hashes[0], hashes[1], hashes[2]);
  }

  @Test
  public void reorderingOneCommit() throws Exception {
    String[] hashes = makeCommits(3);
    myRebaser.reoderCommitsIfNeeded(myRoot, myFirstCommit, Collections.singletonList(hashes[2]));
    assertCommits(myFirstCommit, hashes[2], hashes[0], hashes[1]);
  }

  @Test
  public void reorderingTwoCommits() throws Exception {
    String[] hashes = makeCommits(3);
    myRebaser.reoderCommitsIfNeeded(myRoot, myFirstCommit, Arrays.asList(hashes[2], hashes[1]));
    assertCommits(myFirstCommit, hashes[2], hashes[1], hashes[0]);
  }

  private String[] makeCommits(int number) throws IOException {
    String[] hashes = new String[number];
    for (int i = 0; i < hashes.length; i++) {
      hashes[i] = makeCommit();
    }
    return hashes;
  }

  private String makeCommit() throws IOException {
    VirtualFile file = createFileInCommand(Math.random() + ".txt", "initial" + Math.random());
    myRepo.addCommit();
    return myRepo.lastCommit();
  }

  private void assertCommits(String... commits) throws IOException {
    final String[] hashes = myRepo.execute(false, "rev-list", "--reverse", "HEAD").getStdout().split("\n");
    assertEquals(commits.length, hashes.length);
    for (int i = 0; i < commits.length; i++) {
      assertEquals(hashes[i], commits[i], "Commit #" + i + " doesn't  match");
    }
  }

}
