package com.intellij.openapi.shelf;
/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
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
    String testDataPath = PathManagerEx.getTestDataPath() + "/shelf/" + getTestName(true);
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
