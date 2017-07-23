/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package git4idea.tests;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.testng.Assert;

import java.io.File;

/**
 * @author irengrig
 */
public class GitUpperDirectorySearchTest {
  private LocalFileSystem myLocalFileSystem;
  private IdeaProjectTestFixture myProjectFixture;

  @Before
  public void setUp() throws Exception {
    myProjectFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getClass().getSimpleName()).getFixture();
    myProjectFixture.setUp();

    myLocalFileSystem = LocalFileSystem.getInstance();
  }

  @After
  public void tearDown() throws Exception {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      try {
        myProjectFixture.tearDown();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Test
  public void testDirFinding() {
    final String dirName = "somedir";
    final File file = new File(myProjectFixture.getProject().getBaseDir().getPath(), dirName);
    FileUtil.delete(file);
    Assert.assertTrue(file.mkdir(), "can't create: " + file.getAbsolutePath());
    final File childDir = new File(file, "and/a/path/to");
    Assert.assertTrue(childDir.mkdirs(), "can't create: " + childDir.getAbsolutePath());

    final VirtualFile dir = myLocalFileSystem.refreshAndFindFileByIoFile(file);
    final VirtualFile childFile = myLocalFileSystem.refreshAndFindFileByIoFile(childDir);

    Assert.assertNotNull(dir);
    Assert.assertNotNull(childFile);

    final VirtualFile result = VcsFileUtil.getPossibleBase(childFile, dirName);
    Assert.assertEquals(result, dir);
  }

  @Test
  public void testLongPattern() throws Exception {
    final String dirName = SystemInfo.isFileSystemCaseSensitive ? "somedir/long/path" : "somEdir/lonG/path";
    final File file = new File(myProjectFixture.getProject().getBaseDir().getPath(), "somedir");
    FileUtil.delete(file);
    Assert.assertTrue(file.mkdir(), "can't create: " + file.getAbsolutePath());
    final File childDir = new File(file, "long/path/and/a/path/to");
    Assert.assertTrue(childDir.mkdirs(), "can't create: " + childDir.getAbsolutePath());
    final File a = new File(childDir, "a.txt");
    Assert.assertTrue(a.createNewFile(), "can't create: " + a.getAbsolutePath());

    final VirtualFile dir = myLocalFileSystem.refreshAndFindFileByIoFile(file);
    final VirtualFile childFile = myLocalFileSystem.refreshAndFindFileByIoFile(a);

    Assert.assertNotNull(dir);
    Assert.assertNotNull(childFile);

    final VirtualFile result = VcsFileUtil.getPossibleBase(childFile, dirName.split("/"));
    Assert.assertEquals(result, dir);
  }

  @Test
  public void testLongRepeatedPattern() throws Exception {
    final String dirName = SystemInfo.isFileSystemCaseSensitive ? "somedir/long/path" : "somEdir/lonG/path";
    final File file = new File(myProjectFixture.getProject().getBaseDir().getPath(), "somedir");
    FileUtil.delete(file);
    Assert.assertTrue(file.mkdir(), "can't create: " + file.getAbsolutePath());
    final File childDir = new File(file, "long/path/and/a/path/path/path/to/long/path");
    Assert.assertTrue(childDir.mkdirs(), "can't create: " + childDir.getAbsolutePath());
    final File a = new File(childDir, "a.txt");
    Assert.assertTrue(a.createNewFile(), "can't create: " + a.getAbsolutePath());

    final VirtualFile dir = myLocalFileSystem.refreshAndFindFileByIoFile(file);
    final VirtualFile childFile = myLocalFileSystem.refreshAndFindFileByIoFile(a);

    Assert.assertNotNull(dir);
    Assert.assertNotNull(childFile);

    final VirtualFile result = VcsFileUtil.getPossibleBase(childFile, dirName.split("/"));
    Assert.assertEquals(result, dir);
  }
}
