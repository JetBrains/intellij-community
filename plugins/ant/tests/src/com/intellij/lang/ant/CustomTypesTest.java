/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant;

import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.testFramework.ParsingTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class CustomTypesTest extends ParsingTestCase {

  private static final String myCustomTaskClass = "com.intellij.lang.ant.typedefs.AntCustomTask";

  public CustomTypesTest() {
    super("", "ant");
  }

  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ant") + "/tests/data/psi/customTypes";
  }

  public void testAntCustomTask() throws Exception {
    doTest();
  }

  public void testAntCustomTaskWithClasspath() throws Exception {
    doTest();
  }

  public void testAntCustomTaskWithComplexClasspath() throws Exception {
    doTest();
  }

  @NotNull
  protected AntTypeDefinition doTest() throws Exception {
    String name = getTestName(false);
    String text = loadFile(name + "." + myFileExt);
    PsiFile file = createFile(name + "." + myFileExt, text);
    final AntFile antFile = AntSupport.getAntFile(file);
    antFile.accept(new PsiRecursiveElementVisitor() { });
    final AntTypeDefinition result = antFile.getBaseTypeDefinition(myCustomTaskClass);
    assertNotNull(result);
    return result;
  }

  protected String loadFile(String name) throws IOException {
    String fullName = getTestDataPath() + File.separatorChar + name;
    String text = new String(FileUtil.loadFileText(new File(fullName))).trim();
    text = StringUtil.convertLineSeparators(text);
    return text;
  }
}
