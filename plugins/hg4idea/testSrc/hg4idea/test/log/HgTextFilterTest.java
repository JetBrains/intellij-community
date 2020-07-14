// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package hg4idea.test.log;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.TimedVcsCommit;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogTextFilter;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
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
    cd(getOrCreateProjectBaseDir());
  }

  public void testSubstringCaseSensitivity() throws Exception {
    String smallBrackets = commit("[hg]");
    String bigBrackets = commit("[HG]");
    String smallNoBrackets = commit("letter h");
    String bigNoBrackets = commit("letter G");

    HgLogProvider provider = findLogProvider(myProject);
    String text = "[hg]";

    assertSameElements(Arrays.asList(bigBrackets, smallBrackets),
                       getFilteredCommits(provider, VcsLogFilterObject.fromPattern(text, false, false)));
    assertSameElements(Collections.singletonList(smallBrackets),
                       getFilteredCommits(provider, VcsLogFilterObject.fromPattern(text, false, true)));
    assertSameElements(Arrays.asList(bigNoBrackets, smallNoBrackets, bigBrackets, smallBrackets),
                       getFilteredCommits(provider, VcsLogFilterObject.fromPattern(text, true, false)));
  }

  public void testRegexp() throws Exception {
    String numberedBigBug = commit("Bug 12345");
    commit("bug 12345");
    commit("just a bug");
    String bigBug = commit("not just a bug, but a BUG");
    commit("that\\047s nothing");

    HgLogProvider provider = findLogProvider(myProject);

    assertSameElements(Collections.singletonList(numberedBigBug),
                       getFilteredCommits(provider, VcsLogFilterObject.fromPattern("Bug \\d+", true, true)));
    assertSameElements(Collections.singletonList(bigBug),
                       getFilteredCommits(provider, VcsLogFilterObject.fromPattern("BUG.*", true, true)));
  }

  public void _testRegexpCaseInsensitive() throws Exception {
    String numberedBigBug = commit("Bug 12345");
    String numberedSmallBug = commit("bug 12345");
    String smallBug = commit("just a bug");
    String bigBug = commit("not just a bug, but a BUG");
    commit("that\\047s nothing");

    HgLogProvider provider = findLogProvider(myProject);

    assertSameElements(Arrays.asList(numberedSmallBug, numberedBigBug),
                       getFilteredCommits(provider, VcsLogFilterObject.fromPattern("Bug \\d+", true, false)));
    assertSameElements(Arrays.asList(numberedBigBug, numberedSmallBug, smallBug, bigBug),
                       getFilteredCommits(provider, VcsLogFilterObject.fromPattern("BUG.*", true, false)));
  }

  @NotNull
  private List<String> getFilteredCommits(@NotNull HgLogProvider provider, @NotNull VcsLogTextFilter filter) throws VcsException {
    VcsLogFilterCollection filterCollection = VcsLogFilterObject.collection(filter);
    List<TimedVcsCommit> commits = provider.getCommitsMatchingFilter(getOrCreateProjectBaseDir(), filterCollection, -1);
    return ContainerUtil.map(commits, commit -> commit.getId().asString());
  }

  @NotNull
  private String commit(@NotNull String message) throws IOException {
    String file = "file.txt";
    overwrite(file, "content" + Math.random());
    getOrCreateProjectBaseDir().refresh(false, true);
    hg("add " + file);
    hg("commit -m '" + message + "'");
    return new HgWorkingCopyRevisionsCommand(myProject).tip(getOrCreateProjectBaseDir()).getChangeset();
  }
}
