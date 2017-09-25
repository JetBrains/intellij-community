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

public class ProjectRootUtilSymlinkedFilesTest extends PlatformTestCase {
  private File myNonContentDir;
  private File myNonContentFile;
  private VirtualFile myNonContentVFile;
  
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
    myNonContentDir = FileUtil.createTempDirectory("nonContent", null);
    myNonContentFile = new File(myNonContentDir, "foo.txt");
    assertTrue(myNonContentFile.createNewFile());

    myNonContentVFile = VfsUtil.findFileByIoFile(myNonContentFile, true);
    assertNotNull(myNonContentVFile);

    myContentDir = FileUtil.createTempDirectory("content", null);
    myContentVDir = VfsUtil.findFileByIoFile(myContentDir, true);
    assertNotNull(myContentVDir);
    PsiTestUtil.addContentRoot(getModule(), myContentVDir);

    myLibraryDir = FileUtil.createTempDirectory("library", null);
    myLibraryVDir = VfsUtil.findFileByIoFile(myLibraryDir, true);
    assertNotNull(myLibraryVDir);
    PsiTestUtil.addLibrary(getModule(), myLibraryVDir.getPath());
  }

  public void testNoFilesInContent() {
    assertEquals(myNonContentVFile, ProjectRootUtil.findSymlinkedFileInContent(getProject(), myNonContentVFile));
  }

  public void testLinkedDirInContent() {
    doTestLinkedDirInProjectRoots(true);
  }

  public void testLinkedDirInLibrary() {
    doTestLinkedDirInProjectRoots(false);
  }

  public void doTestLinkedDirInProjectRoots(boolean content) {
    String linkedPath = (content ? myContentDir : myLibraryDir).getPath() + "/linked";
    IoTestUtil.createSymLink(myNonContentDir.getPath(), linkedPath, true);
    
    VirtualFile linkedVFile = VfsUtil.findFileByIoFile(new File(linkedPath, myNonContentFile.getName()), true);
    
    assertEquals(linkedVFile, ProjectRootUtil.findSymlinkedFileInContent(getProject(), myNonContentVFile));
  }

  // not supported
  public void _testLinkedFileInSources() {
    String linkedPath = myContentDir.getPath() + "/linked.txt";
    IoTestUtil.createSymLink(myNonContentVFile.getPath(), linkedPath, true);
    
    VirtualFile linkedVFile = VfsUtil.findFileByIoFile(new File(linkedPath), true);
    assertEquals(linkedVFile, ProjectRootUtil.findSymlinkedFileInContent(getProject(), myNonContentVFile));
  }
  
  public void testFileWithTheSameNotButNotLinked() {
    new WriteAction<Object>() {
      @Override
      protected void run(@NotNull Result<Object> result) throws Throwable {
        myContentVDir.createChildData(this, myNonContentFile.getName());
      }
    }.execute();

    assertEquals(myNonContentVFile, ProjectRootUtil.findSymlinkedFileInContent(getProject(), myNonContentVFile));
  }

  public void testWhenSeveralLinkedAlwaysReturnTheSameFile() {
    String linkedPath1 = myContentDir.getPath() + "/linked1";
    IoTestUtil.createSymLink(myNonContentDir.getPath(), linkedPath1, true);
    VirtualFile linkedVFile1 = VfsUtil.findFileByIoFile(new File(linkedPath1, myNonContentFile.getName()), true);
    assertNotNull(linkedVFile1);

    String linkedPath2 = myContentDir.getPath() + "/linked2";
    IoTestUtil.createSymLink(myNonContentDir.getPath(), linkedPath2, true);
    VirtualFile linkedVFile2 = VfsUtil.findFileByIoFile(new File(linkedPath2, myNonContentFile.getName()), true);
    assertNotNull(linkedVFile2);

    VirtualFile found = ProjectRootUtil.findSymlinkedFileInContent(getProject(), myNonContentVFile);
    assertTrue(found.equals(linkedVFile1) || found.equals(linkedVFile2));
    for(int i = 0; i  < 10; i++) {
      assertEquals("try: " + i, found, ProjectRootUtil.findSymlinkedFileInContent(getProject(), myNonContentVFile));
    }
  }

  public void testLinkedAndCanonicalUnderContent() {
    doTestLinkedAndCanonicalUnderRoot(true);
  }
  
  public void testLinkedAndCanonicalUnderLibraryRoot() {
    doTestLinkedAndCanonicalUnderRoot(false);
  }

  private void doTestLinkedAndCanonicalUnderRoot(boolean content) {
    String linkedPath = myContentDir.getPath() + "/linked";
    IoTestUtil.createSymLink(myNonContentDir.getPath(), linkedPath, true);

    VirtualFile linkedVFile = VfsUtil.findFileByIoFile(new File(linkedPath, myNonContentFile.getName()), true);
    assertEquals(linkedVFile, ProjectRootUtil.findSymlinkedFileInContent(getProject(), myNonContentVFile));
    if(content) {
      PsiTestUtil.addContentRoot(getModule(), myNonContentVFile.getParent());
    } else {
      PsiTestUtil.addLibrary(getModule(), myNonContentVFile.getParent().getPath());
    }
    assertEquals(myNonContentVFile, ProjectRootUtil.findSymlinkedFileInContent(getProject(), myNonContentVFile));
  }

  public void testLinkedFileNotUnderTheContentRoot() throws Exception {
    File nonContentDir = FileUtil.createTempDirectory("nonContent2", null);
    String nonContentLinkedPath = nonContentDir + "/linked";

    IoTestUtil.createSymLink(myNonContentDir.getPath(), nonContentLinkedPath, true);
    VirtualFile nonContentLinkedVFile = VfsUtil.findFileByIoFile(new File(nonContentLinkedPath, myNonContentFile.getName()), true);
    
    assertEquals(nonContentLinkedVFile, ProjectRootUtil.findSymlinkedFileInContent(getProject(), nonContentLinkedVFile));

    String contentLinkedPath = myContentDir + "/linked";
    IoTestUtil.createSymLink(myNonContentDir.getPath(), contentLinkedPath, true);
    VirtualFile contentLinkedVFile = VfsUtil.findFileByIoFile(new File(contentLinkedPath, myNonContentFile.getName()), true);
    
    assertEquals(contentLinkedVFile, ProjectRootUtil.findSymlinkedFileInContent(getProject(), myNonContentVFile));
    assertEquals(contentLinkedVFile, ProjectRootUtil.findSymlinkedFileInContent(getProject(), nonContentLinkedVFile));
    assertEquals(contentLinkedVFile, ProjectRootUtil.findSymlinkedFileInContent(getProject(), contentLinkedVFile));
  }
}
