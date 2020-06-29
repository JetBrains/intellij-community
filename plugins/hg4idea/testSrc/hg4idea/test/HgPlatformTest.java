// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package hg4idea.test;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.test.VcsPlatformTest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgGlobalSettings;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static com.intellij.openapi.vcs.Executor.*;
import static hg4idea.test.HgExecutor.hg;

/**
 * The base class for tests of intellij.vcs.hg plugin.<br/>
 * Extend this test to write a test on Mercurial which has the following features/limitations:
 * <ul>
 * <li>This is a "platform test case", which means that IDEA [almost] production platform is set up before the test starts.</li>
 * <li>Project base directory is the root of everything. It can contain as much nested repositories as needed,
 * but if you need to test the case when hg repository is <b>above</b> the project dir, you need either to adjust this base class,
 * or create another one.</li>
 * <li>Initially one repository is created with the project dir as its root. I. e. all project is under Mercurial.</li>
 * </ul>
 */
public abstract class HgPlatformTest extends VcsPlatformTest {
  protected VirtualFile myRepository;
  protected VirtualFile myChildRepo;
  protected HgVcs myVcs;

  protected static final String COMMIT_MESSAGE = "text";

  @Override
  public void setUp() throws Exception {
    super.setUp();

    cd(projectRoot);
    myVcs = HgVcs.getInstance(myProject);
    assertNotNull(myVcs);
    HgGlobalSettings.getInstance().setHgExecutable(HgExecutor.getHgExecutable());
    debug(HgExecutor.getHgExecutable());
    myVcs.getProjectSettings().setCheckIncomingOutgoing(false);
    myVcs.checkVersion();
    debug(hg("version"));
    createRepository(projectRoot);
    myRepository = projectRoot;
    setUpHgrc(myRepository);

    vcsManager.setDirectoryMappings(Collections.singletonList(new VcsDirectoryMapping(myRepository.getPath(), HgVcs.VCS_NAME)));
  }

  @Override
  protected void tearDown() {
    try {
      HgGlobalSettings.getInstance().setHgExecutable(null);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private static void setUpHgrc(@NotNull VirtualFile repositoryRoot) throws IOException {
    cd(".hg");
    File pluginRoot = new File(PluginPathManager.getPluginHomePath("hg4idea"));
    String pathToHgrc = "testData\\repo\\dot_hg";
    File hgrcFile = new File(new File(pluginRoot, FileUtil.toSystemIndependentName(pathToHgrc)), "hgrc");
    File hgrc = new File(new File(repositoryRoot.getPath(), ".hg"), "hgrc");
    FileUtil.appendToFile(hgrc, FileUtil.loadFile(hgrcFile));
    assertTrue(hgrc.exists());
    repositoryRoot.refresh(false, true);
  }

  protected static void appendToHgrc(@NotNull VirtualFile repositoryRoot, @NotNull String text) throws IOException {
    cd(".hg");
    File hgrc = new File(new File(repositoryRoot.getPath(), ".hg"), "hgrc");
    FileUtil.appendToFile(hgrc, text);
    assertTrue(hgrc.exists());
    repositoryRoot.refresh(false, true);
    cd(repositoryRoot);
  }


  protected static void updateRepoConfig(@NotNull Project project, @Nullable VirtualFile repo) {
    HgRepository hgRepository = HgUtil.getRepositoryManager(project).getRepositoryForRoot(repo);
    assertNotNull(hgRepository);
    hgRepository.updateConfig();
  }

  private static void createRepository(VirtualFile root) {
    initRepo(root.getPath());
  }

  public static void initRepo(String repoRoot) {
    cd(repoRoot);
    hg("init");
    touch("file.txt");
    hg("add file.txt");
    hg("commit -m initial -u asd");
  }

  protected void prepareSecondRepository() throws IOException {
    cd(myRepository);
    hg("clone " + myRepository.getCanonicalPath() + " childRepo");
    myRepository.refresh(false, true);
    myChildRepo = myRepository.findChild("childRepo");
    cd(myChildRepo);
    hg("pull");
    hg("update");
    setUpHgrc(myChildRepo);
    HgTestUtil.updateDirectoryMappings(myProject, myRepository);
    HgTestUtil.updateDirectoryMappings(myProject, myChildRepo);
  }
}
