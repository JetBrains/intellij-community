// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.io.IOException;

abstract public class TestDataPathTestCase extends JavaCodeInsightFixtureTestCase {
  protected VirtualFile myContentRoot;
  protected VirtualFile myContentRootSubdir;
  protected VirtualFile myProjectSubdir;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myContentRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(myFixture.getTempDirPath());
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          myProjectSubdir = myFixture.getProject().getBaseDir().createChildDirectory(this, "projectSubdir");
          myContentRootSubdir = myContentRoot.createChildDirectory(this, "contentRootSubdir");
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addContentRoot(myFixture.getTempDirPath());
  }
}
