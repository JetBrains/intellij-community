// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.rename.RenameProcessor;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;

public class RenamePropertyTest extends MultiFileTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ((StartupManagerImpl)StartupManager.getInstance(myProject)).runPostStartupActivities();
  }

  public void testBundle() {
    doTest("xxx.properties","a.b","c.d");
  }

  public void testNamesConflict() {
    try {
      doTest("xxx.properties","a.b","c.d");
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      Assert.assertEquals("New property name 'c.d' hides existing property", e.getMessage());
      return;
    }
    fail("Conflict was not detected");
  }


  private void doTest(final String fileName, final String key, final String newName) {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) {
        RenamePropertyTest.this.performAction(rootDir, fileName, key, newName);
      }
    });
  }


  private void performAction(final VirtualFile rootDir, String fileName, String key, String newName) {
    VirtualFile child = rootDir.findChild(fileName);

    PropertiesFile file = (PropertiesFile)myPsiManager.findFile(child);
    Property property = (Property)file.findPropertyByKey(key);

    new RenameProcessor(myProject, property, newName, true, true).run();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("java-i18n") + "/";
  }

  @NotNull
  @Override
  protected String getTestRoot() {
    return "/testData/rename/";
  }
}
