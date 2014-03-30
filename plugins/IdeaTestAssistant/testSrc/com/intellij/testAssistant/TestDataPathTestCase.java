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
package com.intellij.testAssistant;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.io.IOException;

/**
 * User: zolotov
 * Date: 9/23/13
 */
abstract public class TestDataPathTestCase extends JavaCodeInsightFixtureTestCase {
  protected VirtualFile myContentRoot;
  protected VirtualFile myContentRootSubdir;
  protected VirtualFile myProjectSubdir;

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
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    moduleBuilder.addContentRoot(myFixture.getTempDirPath());
  }
}
