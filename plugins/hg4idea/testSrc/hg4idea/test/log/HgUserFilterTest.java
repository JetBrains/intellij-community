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

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogUserFilterTest;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsLogManager;
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

import static com.intellij.openapi.vcs.Executor.cd;
import static com.intellij.openapi.vcs.Executor.overwrite;
import static hg4idea.test.HgExecutor.hg;

public class HgUserFilterTest extends HgPlatformTest {
  private VcsLogUserFilterTest myVcsLogUserFilterTest;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    cd(myProject.getBaseDir());

    myVcsLogUserFilterTest = new VcsLogUserFilterTest(findLogProvider(myProject), myProject) {
      @NotNull
      protected String commit(@NotNull VcsUser user) throws IOException {
        String file = "file.txt";
        overwrite(file, "content" + Math.random());
        myProject.getBaseDir().refresh(false, true);
        hg("add " + file);
        hg("commit -m ' Commit by " + user.getName() + "' --user '" + VcsUserUtil.toExactString(user) + "'");
        return new HgWorkingCopyRevisionsCommand(myProject).tip(myProject.getBaseDir()).getChangeset();
      }
    };
  }

  @Override
  protected void tearDown() throws Exception {
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

  public static HgLogProvider findLogProvider(@NotNull Project project) {
    List<VcsLogProvider> providers =
      ContainerUtil.filter(Extensions.getExtensions(VcsLogManager.LOG_PROVIDER_EP, project), new Condition<VcsLogProvider>() {
        @Override
        public boolean value(VcsLogProvider provider) {
          return provider.getSupportedVcs().equals(HgVcs.getKey());
        }
      });
    TestCase.assertEquals("Incorrect number of HgLogProviders", 1, providers.size());
    return (HgLogProvider)providers.get(0);
  }
}
