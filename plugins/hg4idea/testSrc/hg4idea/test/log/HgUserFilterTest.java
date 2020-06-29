// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package hg4idea.test.log;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogUserFilterTest;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.util.VcsUserUtil;
import hg4idea.test.HgPlatformTest;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgWorkingCopyRevisionsCommand;
import org.zmlx.hg4idea.log.HgLogProvider;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.vcs.Executor.*;
import static hg4idea.test.HgExecutor.hg;

public class HgUserFilterTest extends HgPlatformTest {
  private VcsLogUserFilterTest myVcsLogUserFilterTest;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    cd(myProject.getBaseDir());

    myVcsLogUserFilterTest = new VcsLogUserFilterTest(findLogProvider(myProject), myProject) {
      @Override
      @NotNull
      protected String commit(@NotNull VcsUser user) throws IOException {
        boolean success;
        int attempt = 0;
        do {
          success = commitAttempt(user);
        }
        while (!success && attempt++ < 10);
        return new HgWorkingCopyRevisionsCommand(myProject).tip(myProject.getBaseDir()).getChangeset();
      }

      private boolean commitAttempt(@NotNull VcsUser user) throws IOException {
        try {
          String file = "file.txt";
          append(file, String.valueOf(Math.random()));
          myProject.getBaseDir().refresh(false, true);
          hg("add " + file);
          hg("commit -m ' Commit by " + user.getName() + "' --user '" + VcsUserUtil.toExactString(user) + "'");
          debug(hg("tip"));
        }
        catch (RuntimeException e) {
          // nothing changed error (hg wrong file status)
          if (StringUtil.containsIgnoreCase(e.getMessage(), "nothing")) {
            debug(e.getMessage());
            return false;
          }
          throw e;
        }
        return true;
      }
    };
  }

  @Override
  protected void tearDown() {
    myVcsLogUserFilterTest = null;
    super.tearDown();
  }

  public void testFullMatching() throws Exception {
    myVcsLogUserFilterTest.testFullMatching();
  }

  public void testSynonyms() throws Exception {
    myVcsLogUserFilterTest.testSynonyms(Collections.emptySet());
  }

  public void testWeirdCharacters() throws Exception {
    myVcsLogUserFilterTest.testWeirdCharacters();
  }

  public void testWeirdNames() throws Exception {
    myVcsLogUserFilterTest.testWeirdNames();
  }

  public void testJeka() throws Exception {
    myVcsLogUserFilterTest.testJeka();
  }

  public void testTurkishLocale() throws Exception {
    myVcsLogUserFilterTest.testTurkishLocale();
  }

  public void testNameAtSurnameEmails() throws Exception {
    myVcsLogUserFilterTest.testNameAtSurnameEmails();
  }

  public static HgLogProvider findLogProvider(@NotNull Project project) {
    List<VcsLogProvider> providers =
      ContainerUtil.filter(VcsLogProvider.LOG_PROVIDER_EP.getExtensions(project),
                           provider -> provider.getSupportedVcs().equals(HgVcs.getKey()));
    TestCase.assertEquals("Incorrect number of HgLogProviders", 1, providers.size());
    return (HgLogProvider)providers.get(0);
  }
}
