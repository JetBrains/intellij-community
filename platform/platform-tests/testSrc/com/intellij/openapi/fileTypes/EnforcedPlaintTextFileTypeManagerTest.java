/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.file.exclude.EnforcedPlainTextFileTypeFactory;
import com.intellij.openapi.file.exclude.EnforcedPlainTextFileTypeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.ui.UIUtil;

/**
 * @author Rustam Vishnyakov
 */
public class EnforcedPlaintTextFileTypeManagerTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testMarkAsPlainText() {
    EnforcedPlainTextFileTypeManager manager = EnforcedPlainTextFileTypeManager.getInstance();
    VirtualFile file = myFixture.getTempDirFixture().createFile("test.java");
    FileType originalType = file.getFileType();
    assertEquals("JAVA", originalType.getName());
    manager.markAsPlainText(getProject(), file);
    UIUtil.dispatchAllInvocationEvents(); // reparseFiles in invokeLater
    FileType changedType = file.getFileType();
    assertEquals(EnforcedPlainTextFileTypeFactory.ENFORCED_PLAIN_TEXT, changedType.getName());
    manager.resetOriginalFileType(getProject(), file);
    FileType revertedType = file.getFileType();
    assertEquals(originalType, revertedType);
  }
}
