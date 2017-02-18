/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package hg4idea.test.log;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.TimedVcsCommit;
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl;
import com.intellij.vcs.log.ui.filter.VcsLogTextFilterImpl;
import hg4idea.test.HgPlatformTest;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.command.HgWorkingCopyRevisionsCommand;
import org.zmlx.hg4idea.log.HgLogProvider;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.vcs.Executor.cd;
import static com.intellij.openapi.vcs.Executor.overwrite;
import static hg4idea.test.HgExecutor.hg;
import static hg4idea.test.log.HgUserFilterTest.findLogProvider;

public class HgTextFilterTest extends HgPlatformTest {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    cd(myProject.getBaseDir());
  }

  public void testSubstringCaseSensitivity() throws Exception {
    String smallBrackets = commit("[hg]");
    String bigBrackets = commit("[HG]");
    String smallNoBrackets = commit("letter h");
    String bigNoBrackets = commit("letter G");

    HgLogProvider provider = findLogProvider(myProject);
    String text = "[hg]";

    assertSameElements(Arrays.asList(bigBrackets, smallBrackets),
                       getFilteredCommits(provider, new VcsLogTextFilterImpl(text, false, false)));
    assertSameElements(Collections.singletonList(smallBrackets),
                       getFilteredCommits(provider, new VcsLogTextFilterImpl(text, false, true)));
    assertSameElements(Arrays.asList(bigNoBrackets, smallNoBrackets, bigBrackets, smallBrackets),
                       getFilteredCommits(provider, new VcsLogTextFilterImpl(text, true, false)));
  }

  public void testRegexp() throws Exception {
    String numberedBigBug = commit("Bug 12345");
    commit("bug 12345");
    commit("just a bug");
    String bigBug = commit("not just a bug, but a BUG");
    commit("that\\047s nothing");

    HgLogProvider provider = findLogProvider(myProject);

    assertSameElements(Collections.singletonList(numberedBigBug),
                       getFilteredCommits(provider, new VcsLogTextFilterImpl("Bug \\d+", true, true)));
    assertSameElements(Collections.singletonList(bigBug),
                       getFilteredCommits(provider, new VcsLogTextFilterImpl("BUG.*", true, true)));
  }

  public void _testRegexpCaseInsensitive() throws Exception {
    String numberedBigBug = commit("Bug 12345");
    String numberedSmallBug = commit("bug 12345");
    String smallBug = commit("just a bug");
    String bigBug = commit("not just a bug, but a BUG");
    commit("that\\047s nothing");

    HgLogProvider provider = findLogProvider(myProject);

    assertSameElements(Arrays.asList(numberedSmallBug, numberedBigBug),
                       getFilteredCommits(provider, new VcsLogTextFilterImpl("Bug \\d+", true, false)));
    assertSameElements(Arrays.asList(numberedBigBug, numberedSmallBug, smallBug, bigBug),
                       getFilteredCommits(provider, new VcsLogTextFilterImpl("BUG.*", true, false)));
  }

  @NotNull
  private List<String> getFilteredCommits(@NotNull HgLogProvider provider, @NotNull VcsLogTextFilterImpl filter) throws VcsException {
    VcsLogFilterCollectionImpl filterCollection = new VcsLogFilterCollectionImpl(null, null, null, null,
                                                                                 filter, null, null);
    List<TimedVcsCommit> commits = provider.getCommitsMatchingFilter(myProject.getBaseDir(), filterCollection, -1);
    return ContainerUtil.map(commits, commit -> commit.getId().asString());
  }

  @NotNull
  private String commit(@NotNull String message) throws IOException {
    String file = "file.txt";
    overwrite(file, "content" + Math.random());
    myProject.getBaseDir().refresh(false, true);
    hg("add " + file);
    hg("commit -m '" + message + "'");
    return new HgWorkingCopyRevisionsCommand(myProject).tip(myProject.getBaseDir()).getChangeset();
  }
}
