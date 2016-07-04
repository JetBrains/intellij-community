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
package com.intellij.openapi.roots;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class ProjectRootUtilSymlinkedFilesTest extends PlatformTestCase {
  private File myCanonicalDir;
  private File myCanonicalFile;
  private VirtualFile myCanonicalVFile;
  private File myContentDir;
  private VirtualFile myContentVDir;
  private File myLibraryDir;
  private VirtualFile myLibraryVDir;

  @Override
  protected boolean shouldRunTest() {
    return super.shouldRunTest() && SystemInfo.areSymLinksSupported;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myCanonicalDir = FileUtil.createTempDirectory("canonical", null);
    myCanonicalFile = new File(myCanonicalDir, "foo.txt");
    assertTrue(myCanonicalFile.createNewFile());

    myCanonicalVFile = VfsUtil.findFileByIoFile(myCanonicalFile, true);
    assertNotNull(myCanonicalVFile);

    myContentDir = FileUtil.createTempDirectory("content", null);
    myContentVDir = VfsUtil.findFileByIoFile(myContentDir, true);
    assertNotNull(myContentVDir);
    PsiTestUtil.addContentRoot(getModule(), myContentVDir);

    myLibraryDir = FileUtil.createTempDirectory("library", null);
    myLibraryVDir = VfsUtil.findFileByIoFile(myLibraryDir, true);
    assertNotNull(myLibraryVDir);
    PsiTestUtil.addLibrary(getModule(), myLibraryVDir.getPath());
  }

  public void testNoFilesInContent() throws Exception {
    assertEquals(myCanonicalVFile, ProjectRootUtil.findSymlinkedFileInContent(getProject(), myCanonicalVFile));
  }

  public void testLinkedDirInContent() throws Exception {
    doTestLinkedDirInProjectRoots(true);
  }

  public void testLinkedDirInLibrary() throws Exception {
    doTestLinkedDirInProjectRoots(false);
  }

  public void doTestLinkedDirInProjectRoots(boolean content) throws Exception {
    String linkedPath = (content ? myContentDir : myLibraryDir).getPath() + "/linked";
    IoTestUtil.createSymLink(myCanonicalDir.getPath(), linkedPath, true);
    
    VirtualFile linkedVFile = VfsUtil.findFileByIoFile(new File(linkedPath, myCanonicalFile.getName()), true);
    
    assertEquals(linkedVFile, ProjectRootUtil.findSymlinkedFileInContent(getProject(), myCanonicalVFile));
  }

  // not supported
  public void _testLinkedFileInSources() throws Exception {
    String linkedPath = myContentDir.getPath() + "/linked.txt";
    IoTestUtil.createSymLink(myCanonicalVFile.getPath(), linkedPath, true);
    
    VirtualFile linkedVFile = VfsUtil.findFileByIoFile(new File(linkedPath), true);
    assertEquals(linkedVFile, ProjectRootUtil.findSymlinkedFileInContent(getProject(), myCanonicalVFile));
  }
  
  public void testFileWithTheSameNotButNotLinked() throws Exception {
    new WriteAction<Object>() {
      @Override
      protected void run(@NotNull Result<Object> result) throws Throwable {
        myContentVDir.createChildData(this, myCanonicalFile.getName());
      }
    }.execute();

    assertEquals(myCanonicalVFile, ProjectRootUtil.findSymlinkedFileInContent(getProject(), myCanonicalVFile));
  }

  public void testWhenSeveralLinkedAlwaysReturnTheSameFile() throws Exception {
    String linkedPath1 = myContentDir.getPath() + "/linked1";
    IoTestUtil.createSymLink(myCanonicalDir.getPath(), linkedPath1, true);
    VirtualFile linkedVFile1 = VfsUtil.findFileByIoFile(new File(linkedPath1, myCanonicalFile.getName()), true);
    assertNotNull(linkedVFile1);

    String linkedPath2 = myContentDir.getPath() + "/linked2";
    IoTestUtil.createSymLink(myCanonicalDir.getPath(), linkedPath2, true);
    VirtualFile linkedVFile2 = VfsUtil.findFileByIoFile(new File(linkedPath2, myCanonicalFile.getName()), true);
    assertNotNull(linkedVFile2);

    VirtualFile found = ProjectRootUtil.findSymlinkedFileInContent(getProject(), myCanonicalVFile);
    assertTrue(found.equals(linkedVFile1) || found.equals(linkedVFile2));
    for(int i = 0; i  < 10; i++) {
      assertEquals("try: " + i, found, ProjectRootUtil.findSymlinkedFileInContent(getProject(), myCanonicalVFile));
    }
  }

  public void testLinkedAndCanonicalUnderContent() throws Exception {
    doTestLinkedAndCanonicalUnderRoot(true);
  }
  
  public void testLinkedAndCanonicalUnderLibraryRoot() throws Exception {
    doTestLinkedAndCanonicalUnderRoot(false);
  }

  private void doTestLinkedAndCanonicalUnderRoot(boolean content) throws InterruptedException, IOException {
    String linkedPath = myContentDir.getPath() + "/linked";
    IoTestUtil.createSymLink(myCanonicalDir.getPath(), linkedPath, true);

    VirtualFile linkedVFile = VfsUtil.findFileByIoFile(new File(linkedPath, myCanonicalFile.getName()), true);
    assertEquals(linkedVFile, ProjectRootUtil.findSymlinkedFileInContent(getProject(), myCanonicalVFile));
    if(content) {
      PsiTestUtil.addContentRoot(getModule(), myCanonicalVFile.getParent());
    } else {
      PsiTestUtil.addLibrary(getModule(), myCanonicalVFile.getParent().getPath());
    }
    assertEquals(myCanonicalVFile, ProjectRootUtil.findSymlinkedFileInContent(getProject(), myCanonicalVFile));
  }
}
