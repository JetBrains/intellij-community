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
package hg4idea.test.config;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsTestUtil;
import hg4idea.test.HgPlatformTest;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static com.intellij.openapi.vcs.Executor.cd;

/**
 * @author Nadya Zabrodina
 */
public class HgConfigTest extends HgPlatformTest {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    try {
      prepareSecondRepository();
    }
    catch (Exception | Error e) {
      tearDown();
      throw e;
    }
  }

  public void testDefaultPathInClonedRepo() {
    cd(myChildRepo);
    updateRepoConfig(myProject, myChildRepo);
    final String defaultPath = HgUtil.getRepositoryDefaultPath(myProject, myChildRepo);
    assertNotNull(defaultPath);
    assertEquals(myRepository.getPath(),
                 FileUtil.toSystemIndependentName(defaultPath));
  }

  public void testPushPathInClonedRepo() throws IOException {
    checkDefaultPushPath();
  }

  public void testPushPathInClonedRepoWithDebugOption() throws IOException {
    cd(myChildRepo);
    appendToHgrc(myChildRepo, "\n[ui]\n" +
                              "debug=True");
    checkDefaultPushPath();
  }

  private void checkDefaultPushPath() throws IOException {
    cd(myChildRepo);
    String pushPath = "somePath";
    appendToHgrc(myChildRepo, "\n[paths]\n" +
                              "default-push = " + pushPath);
    appendToHgrc(myChildRepo, "\n[paths]\n" +
                              "default:pushurl = " + pushPath);
    updateRepoConfig(myProject, myChildRepo);
    final String defaultPushPath = HgUtil.getRepositoryDefaultPushPath(myProject, myChildRepo);
    assertNotNull(defaultPushPath);
    String absolutePath = FileUtil.toSystemIndependentName(myChildRepo.getPath() + "/" + pushPath);
    // after default-push config deprecation around version 3.7 ,hg reports the same value that is contained in hgrc file
    assertTrue(absolutePath.contains(FileUtil.toSystemIndependentName(defaultPushPath)));
  }

  public void testPushPathWithoutAppropriateConfig() {
    cd(myChildRepo);
    final String defaultPushPath = HgUtil.getRepositoryDefaultPushPath(myProject, myChildRepo);
    assertNotNull(defaultPushPath);
    assertEquals(myRepository.getPath(),
                 FileUtil.toSystemIndependentName(defaultPushPath));
  }

  public void testMultiPathConfig() throws IOException {
    cd(myChildRepo);
    final String path1 = "https://bitbucket.org/nadushnik/hgtestrepo";
    final String path2 = "https://bitbucket.org/nadushnik/javarepo";
    final String path3 = "https://bitbucket.org/nadushnik/hgTestRepo2";
    appendToHgrc(myChildRepo, "\n[paths]" +
                              "\npath1=" + path1 +
                              "\npath2=" + path2 +
                              "\npath3=" + path3);
    updateRepoConfig(myProject, myChildRepo);
    final Collection<String> paths = HgUtil.getRepositoryPaths(myProject, myChildRepo);
    final Collection<String> expectedPaths = Arrays.asList(FileUtil.toSystemDependentName(myRepository.getPath()), path1, path2, path3);
    VcsTestUtil.assertEqualCollections(paths, expectedPaths);
  }

  public void testLargeExtensionInClonedRepo() throws IOException {
    cd(myChildRepo);
    appendToHgrc(myChildRepo, "\n[extensions]\n" +
                              "largefiles =");
    updateRepoConfig(myProject, myChildRepo);
    assertNotNull(HgUtil.getConfig(myProject, myChildRepo, "extensions", "largefiles"));
  }
}
