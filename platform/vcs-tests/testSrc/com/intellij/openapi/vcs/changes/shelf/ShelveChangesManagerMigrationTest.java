// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager.getInstance;

public class ShelveChangesManagerMigrationTest extends PlatformTestCase {

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
    ShelveChangesManager shelveChangesManager = getInstance(myProject);
    shelveChangesManager.loadState(element);
    if (migrateResources) {
      checkAndMigrateOldPatchResourcesToNewSchemeStorage(shelveChangesManager);
    }
    PlatformTestUtil.saveProject(myProject);
    shelfDir.refresh(false, true);
    PlatformTestUtil.assertDirectoriesEqual(afterDir, shelfDir);
  }

  /**
   * Should be called only once: when Settings Repository plugin runs first time
   */
  private static void checkAndMigrateOldPatchResourcesToNewSchemeStorage(@NotNull ShelveChangesManager shelveChangesManager)
    throws IOException {
    for (ShelvedChangeList list : shelveChangesManager.getAllLists()) {
      File newPatchDir = new File(shelveChangesManager.getShelfResourcesDirectory(), list.getName());
      ShelvedChangeList migrated = shelveChangesManager.createChangelistCopyWithChanges(list, newPatchDir);
      shelveChangesManager.saveListAsScheme(migrated);
      shelveChangesManager.clearShelvedLists(Collections.singletonList(list), false);
    }
  }
}
