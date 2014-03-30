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
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

import java.io.IOException;

public class FileTypeManagerTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testAutoDetectTextFileFromContents() throws IOException {
    VirtualFile vFile = myFixture.getTempDirFixture().createFile("test.xxxxxxxx");
    VfsUtil.saveText(vFile, "text");

    FileType type = vFile.getFileType();
    assertEquals(UnknownFileType.INSTANCE, type);

    PsiFile psiFile = ((PsiManagerEx)PsiManager.getInstance(myFixture.getProject())).getFileManager().findFile(vFile); // autodetect text file if needed
    assertNotNull(psiFile);
    assertEquals(PlainTextFileType.INSTANCE, vFile.getFileType());
  }

  public void testIgnoredFiles() throws IOException {
    VirtualFile vFile = myFixture.getTempDirFixture().createFile(".svn", "");
    assertTrue(FileTypeManager.getInstance().isFileIgnored(vFile));
    vFile.delete(this);

    vFile = myFixture.getTempDirFixture().createFile("a.txt", "");
    assertFalse(FileTypeManager.getInstance().isFileIgnored(vFile));
  }
}
