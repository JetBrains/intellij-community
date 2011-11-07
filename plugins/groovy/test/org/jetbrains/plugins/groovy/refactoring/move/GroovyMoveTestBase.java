/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.io.File;
import java.util.ArrayList;

/**
 * @author Max Medvedev
 */
public abstract class GroovyMoveTestBase extends JavaCodeInsightFixtureTestCase {
  protected void doTest(String newPackageName, String... names) {
    try {
      String root = PathManager.getHomePath().replace(File.separatorChar, '/') + getBasePath() + getTestName(true);

      String rootBefore = root + "/before";
      PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk17());
      ArrayList<File> filesToDelete = new ArrayList<File>();
      VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myFixture.getProject(), myModule, rootBefore, filesToDelete);

      perform(rootDir, newPackageName, names);

      String rootAfter = root + "/after";
      VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
      myFixture.getProject().getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
      PlatformTestUtil.assertDirectoriesEqual(rootDir2, rootDir, PlatformTestUtil.CVS_FILE_FILTER);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  abstract void perform(VirtualFile root, String moveTo, String... names);
}
