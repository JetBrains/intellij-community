/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.internationalization;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.ui.UIUtil;
import com.siyeh.ig.LightInspectionTestCase;

import java.nio.charset.Charset;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryUnicodeEscapeInspectionTest extends LightInspectionTestCase {

  public void testUnnecessaryUnicodeEscape() {
    myFixture.configureByFile(getTestName(false) + ".java");
    final VirtualFile vFile = myFixture.getFile().getVirtualFile();
    Charset ascii = CharsetToolkit.forName("US-ASCII");
    EncodingManager.getInstance().setEncoding(vFile, ascii);
    UIUtil.dispatchAllInvocationEvents(); // reload in invokeLater
    assertEquals(ascii, vFile.getCharset());
    myFixture.testHighlighting(true, false, false);
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new UnnecessaryUnicodeEscapeInspection();
  }
}
