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
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightPlatformLangTestCase;

import java.io.File;
import java.io.IOException;

public class RealFileDocumentManagerTest extends LightPlatformLangTestCase {
  public void testFileTypeModificationDocumentPreservation() throws Exception {
    File ioFile = IoTestUtil.createTestFile("test.html", "<html>some text</html>");
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
    assertNotNull(ioFile.getPath(), file);

    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    Document original = documentManager.getDocument(file);
    assertNotNull(file.getPath(), original);

    renameFile(file, "test.wtf");
    Document afterRename = documentManager.getDocument(file);
    assertTrue(afterRename + " != " + original, afterRename == original);
  }

  public void testFileTypeChangeDocumentDetach() throws Exception {
    File ioFile = IoTestUtil.createTestFile("test.html", "<html>some text</html>");
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
    assertNotNull(ioFile.getPath(), file);

    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    Document original = documentManager.getDocument(file);
    assertNotNull(file.getPath(), original);

    renameFile(file, "test.png");
    Document afterRename = documentManager.getDocument(file);
    assertNull(afterRename + " != null", afterRename);
  }

  private static void renameFile(VirtualFile file, String newName) throws IOException {
    AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(RealFileDocumentManagerTest.class);
    try {
      file.rename(RealFileDocumentManagerTest.class, newName);
    }
    finally {
      token.finish();
    }
  }
}
