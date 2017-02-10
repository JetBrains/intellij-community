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

import com.intellij.lang.ant.dom.AntDomCustomElement;
import com.intellij.lang.ant.dom.AntDomElement;
import com.intellij.lang.ant.dom.AntDomProject;
import com.intellij.lang.ant.dom.AntDomRecursiveVisitor;
import com.intellij.lang.ant.typedefs.AntCustomTask;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;

import java.io.File;
import java.io.IOException;

public class CustomTypesTest extends LightCodeInsightFixtureTestCase {

  private static final String myCustomTaskClass = "com.intellij.lang.ant.typedefs.AntCustomTask";

  public CustomTypesTest() {
  }

  protected String getMyTestDataPath() {
    return PluginPathManager.getPluginHomePath("ant") + "/tests/data/psi/customTypes";
  }

  public void testAntCustomTask() throws Exception {
    doTest();
  }

  protected void doTest() throws Exception {
    String name = getTestName(false);
    String text = loadFile(name + ".ant");
    PsiFile file = myFixture.addFileToProject(name + ".ant", text);
    final AntDomProject antProject = AntSupport.getAntDomProject(file);
    final Ref<Boolean> found = new Ref<>(false);
    antProject.accept(new AntDomRecursiveVisitor() {

      @Override
      public void visitAntDomElement(AntDomElement element) {
        if (!found.get()) {
          super.visitAntDomElement(element);
        }
      }

      @Override
      public void visitAntDomCustomElement(AntDomCustomElement element) {
        final Class clazz = element.getDefinitionClass();
        if (clazz != null && AntCustomTask.class.getName().equals(clazz.getName())) {
          found.set(true);
        }
        else {
          super.visitAntDomElement(element);
        }
      }
    });
    assertTrue(found.get());
  }

  protected String loadFile(String name) throws IOException {
    String fullName = getMyTestDataPath() + File.separatorChar + name;
    String text = FileUtil.loadFile(new File(fullName)).trim();
    text = StringUtil.convertLineSeparators(text);
    final String root = PathUtil.getJarPathForClass(this.getClass());
    final String placeholder = "<_classpath_>";
    final int index = text.indexOf(placeholder);
    if (index > 0) {
      final String before = text.substring(0, index);
      final String after = text.substring(index + placeholder.length());
      text = before + FileUtil.toSystemIndependentName(root) + after;
    }
    return text;
  }
}
