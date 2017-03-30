/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.shelf;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jdom.Element;

import java.io.File;

public class ShelveChangesManagerTest extends PlatformTestCase {

  public void testMigrateInfo() throws Exception {
    doTest();
  }

  public void testMigrateInfoRecycled() throws Exception {
    doTest();
  }

  public void testMigrateInfoWithBinaries() throws Exception {
    doTest();
  }

  public void testMigrateInfoWithVeryLongDescription() throws Exception {
    doTest();
  }

  public void testMigrateWithResources() throws Exception {
    doTest(true);
  }

  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(boolean migrateResources) throws Exception {
    String testDataPath = VcsTestUtil.getTestDataPath() + "/shelf/" + getTestName(true);
    File beforeFile = new File(testDataPath, "before");
    File afterFile = new File(testDataPath, "after");
    VirtualFile afterDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(afterFile);
    File shelfFile = new File(myProject.getBasePath(), ".shelf");
    FileUtil.createDirectory(shelfFile);
    myFilesToDelete.add(shelfFile);
    FileUtil.copyDir(beforeFile, shelfFile);
    VirtualFile shelfDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(shelfFile);
    assertNotNull(shelfDir);
    File beforeXmlInfo = new File(testDataPath, "before.xml");
    assert (beforeXmlInfo.exists());
    Element element = JDOMUtil.load(beforeXmlInfo);
    ShelveChangesManager shelveChangesManager = ShelveChangesManager.getInstance(myProject);
    shelveChangesManager.readExternal(element);
    if (migrateResources) {
      shelveChangesManager.checkAndMigrateOldPatchResourcesToNewSchemeStorage();
    }
    shelfDir.refresh(false, true);
    PlatformTestUtil.saveProject(myProject);
    PlatformTestUtil.assertDirectoriesEqual(afterDir, shelfDir);
  }
}
