/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.move

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

/**
 * @author Max Medvedev
 */
public abstract class GroovyMoveTestBase extends JavaCodeInsightFixtureTestCase {
  protected void doTest(String destination, String... names) {
    String root = PathManager.homePath.replace(File.separatorChar, '/' as char) + basePath + getTestName(true);

    String rootBefore = "$root/before";
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.mockJdk17);
    ArrayList<File> filesToDelete = new ArrayList<File>();
    VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myFixture.project, myModule, rootBefore, filesToDelete);
    def localFileSystem = LocalFileSystem.instance
    localFileSystem.refresh(false)
    if (!perform(rootDir, destination, names)) return

    String rootAfter = "$root/after";
    VirtualFile rootDir2 = localFileSystem.findFileByPath(rootAfter.replace(File.separatorChar, '/' as char));
    PostprocessReformattingAspect.getInstance(myFixture.project).doPostponedFormatting();
    localFileSystem.refresh(false)
    PlatformTestUtil.assertDirectoriesEqual(rootDir2, rootDir);
  }

  abstract boolean perform(VirtualFile root, String moveTo, String... names)
}
