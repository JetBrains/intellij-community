/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package hg4idea.test;

import com.intellij.dvcs.test.MockProject;
import com.intellij.dvcs.test.MockVirtualFile;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.io.IOException;

import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * @author Nadya Zabrodina
 */
public class HgLightTest extends HgExecutor {

  //private static final String USER_NAME = "John Doe";
  //private static final String USER_EMAIL = "John.Doe@example.com";

  /**
   * The file system root of test files.
   * Automatically deleted on {@link #tearDown()}.
   * Tests should create new files only inside this directory.
   */
  protected String myTestRoot;

  /**
   * The file system root of the project. All project should locate inside this directory.
   */
  protected String myProjectRoot;

  protected MockProject myProject;
  protected HgTestPlatformFacade myPlatformFacade;

  @Before
  public void setUp() {
    assumeTrue(false);
    try {
      myTestRoot = FileUtil.createTempDirectory("", "").getPath();
    }
    catch (IOException e) {
      fail("Can not start test case!\n");  //todo change
    }
    cd(myTestRoot);
    myProjectRoot = mkdir("project");
    myProject = new MockProject(myProjectRoot);
    myPlatformFacade = new HgTestPlatformFacade();
    cd(".hg");
    File pluginRoot = new File(PluginPathManager.getPluginHomePath("hg4idea"));
    File hgrcFile = new File(new File(pluginRoot, "testData\\repo\\dot_hg"), "hgrc");
    File hgrc = new File(new File(myProjectRoot, ".hg"), "hgrc");
    try {
      FileUtil.copy(hgrcFile, hgrc);
    }
    catch (IOException e) {
      e.printStackTrace();
      fail("Can not copy hgrc file.");
    }
    assertTrue(hgrc.exists());
  }


  @After
  public void tearDown() {
    if (myTestRoot != null) {
      FileUtil.delete(new File(myTestRoot));
    }
    if (myProject != null) {
      Disposer.dispose(myProject);
    }
  }

  protected MockVirtualFile createRepository(String rootDir) {
    initRepo(rootDir);
    return new MockVirtualFile(rootDir);
  }

  private void initRepo(String repoRoot) {
    cd(repoRoot);
    hg("init");
    touch("file.txt");
    hg("add file.txt");
    hg("commit -m initial");
  }
}
