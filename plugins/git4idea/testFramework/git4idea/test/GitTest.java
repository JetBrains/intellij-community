/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.test;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.vcs.AbstractVcsTestCase;
import com.intellij.ui.GuiUtils;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ui.UIUtil;
import git4idea.GitVcs;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Map;

import static org.testng.Assert.assertNotNull;

/**
 * The common ancestor for git test cases which need git executable.
 * These tests can be executed only if git is installed in the system and IDEA_TEST_GIT_EXECUTABLE_PATH targets to the folder which
 * contains git executable.
 * @author Kirill Likhodedov
 * @deprecated Use {@link GitLightTest}
 */
@Deprecated
public abstract class GitTest extends AbstractVcsTestCase {

  protected GitTestRepository myRepo;
  protected GitTestRepository myParentRepo;
  protected GitTestRepository myBrotherRepo;

  private File myProjectDir;

  private TempDirTestFixture myProjectDirFixture;
  private TempDirTestFixture myParentDirFixture;
  private TempDirTestFixture myBrotherDirFixture;

  protected static final String MAIN_USER_NAME = "John Smith";
  protected static final String MAIN_USER_EMAIL = "john.smith@email.com";
  protected static final String BROTHER_USER_NAME = "Bob Doe";
  protected static final String BROTHER_USER_EMAIL = "bob.doe@email.com";
  protected GitVcs myVcs;

  @BeforeMethod
  protected void setUp(final Method testMethod) throws Exception {
    System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, "PlatformLangXml");
    myProjectDirFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();
    myProjectDirFixture.setUp();
    myProjectDir = new File(myProjectDirFixture.getTempDirPath());

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          initProject(myProjectDir, testMethod.getName());
          initRepositories();
          activateVCS(GitVcs.NAME);
        } catch (Exception e) {
          throw new RuntimeException("Exception initializing the test", e);
        }
      }
    });

    myVcs = GitVcs.getInstance(myProject);
    assertNotNull(myVcs);
    myTraceClient = true;
    doActionSilently(VcsConfiguration.StandardConfirmation.ADD);
    doActionSilently(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  @AfterMethod
  protected void tearDown() throws Exception {
    GuiUtils.runOrInvokeAndWait(new Runnable() {
      @Override
      public void run() {
        try {
          tearDownProject();
          myProjectDirFixture.tearDown();
          myBrotherDirFixture.tearDown();
          myParentDirFixture.tearDown();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
  }

  @Override
  protected String getPluginName() {
    return "Git4Idea";
  }

  private void initRepositories() throws Exception {
    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    myParentDirFixture = fixtureFactory.createTempDirTestFixture();
    myParentDirFixture.setUp();
    myBrotherDirFixture = fixtureFactory.createTempDirTestFixture();
    myBrotherDirFixture.setUp();

    myParentRepo = GitTestRepository.init(new File(myParentDirFixture.getTempDirPath()));

    myRepo = GitTestRepository.clone(myParentRepo, myProjectDir);
    myRepo.setName(MAIN_USER_NAME, MAIN_USER_EMAIL);
    myRepo.refresh();

    myBrotherRepo = GitTestRepository.clone(myParentRepo, new File(myBrotherDirFixture.getTempDirPath()));
    myBrotherRepo.setName(BROTHER_USER_NAME, BROTHER_USER_EMAIL);
  }

  protected void doActionSilently(final VcsConfiguration.StandardConfirmation op) {
    setStandardConfirmation(GitVcs.NAME, op, VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY);
  }

  protected String tos(FilePath fp) {
    return FileUtil.getRelativePath(myProjectDir, fp.getIOFile());
  }

  protected String tos(Change change) {
    switch (change.getType()) {
      case NEW: return "A: " + tos(change.getAfterRevision());
      case DELETED: return "D: " + tos(change.getBeforeRevision());
      case MOVED: return "M: " + tos(change.getBeforeRevision()) + " -> " + tos(change.getAfterRevision());
      case MODIFICATION: return "M: " + tos(change.getAfterRevision());
      default: return "~: " +  tos(change.getBeforeRevision()) + " -> " + tos(change.getAfterRevision());
    }
  }

  protected String tos(ContentRevision revision) {
    return tos(revision.getFile());
  }

  protected String tos(Map<FilePath, Change> changes) {
    StringBuilder stringBuilder = new StringBuilder("[");
    for (Change change : changes.values()) {
      stringBuilder.append(tos(change)).append(", ");
    }
    stringBuilder.append("]");
    return stringBuilder.toString();
  }

}
