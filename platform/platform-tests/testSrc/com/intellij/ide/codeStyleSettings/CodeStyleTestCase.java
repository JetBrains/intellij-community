/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.codeStyleSettings;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.options.SchemeFactory;
import com.intellij.openapi.options.SchemeImportException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeImpl;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeXmlImporter;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;

public abstract class CodeStyleTestCase extends LightPlatformTestCase {

  protected static final String BASE_PATH = PathManagerEx.getTestDataPath("/../../../platform/platform-tests/testData/codeStyle/");

  @SuppressWarnings("Duplicates")
  public static void assertXmlOutputEquals(String expected, Element root) throws IOException {
    StringWriter writer = new StringWriter();
    Format format = Format.getPrettyFormat();
    format.setLineSeparator("\n");
    new XMLOutputter(format).output(root, writer);
    String actual = writer.toString();
    assertEquals(expected, actual);
  }

  protected static Element createOption(String name, String value) {
    Element optionElement = new Element("option");
    optionElement.setAttribute("name", name);
    optionElement.setAttribute("value", value);
    return optionElement;
  }

  @Nullable
  protected String getTestDir() {
    return null;
  }

  @NotNull
  protected final String getTestDataPath() {
    String testDir = getTestDir();
    return getBasePath() + (testDir != null ? testDir : "") + File.separator;
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return new LightProjectDescriptor() {
      @Override
      public void setUpProject(@NotNull Project project, @NotNull SetupHandler handler) throws Exception {
        setupProject();
        super.setUpProject(project, handler);
      }
    };
  }

  protected void setupProject() throws Exception {}

  protected CodeStyleSettings importSettings() throws SchemeImportException {
    final CodeStyleScheme targetScheme = new CodeStyleSchemeImpl("Test", false, null);
    SchemeFactory<CodeStyleScheme> schemeFactory = new SchemeFactory<CodeStyleScheme>() {
      @Override
      public CodeStyleScheme createNewScheme(@Nullable String name) {
        return targetScheme;
      }
    };
    File ioFile = new File(getTestDataPath() + getTestName(true) + ".xml");
    assertExists(ioFile);
    VirtualFile vFile = VfsUtil.findFileByIoFile(ioFile, true);
    CodeStyleSchemeXmlImporter importer = new CodeStyleSchemeXmlImporter();
    return importer.importScheme(getProject(), vFile, targetScheme, schemeFactory).getCodeStyleSettings();
  }

  protected String getBasePath() {
    return BASE_PATH;
  }
}
