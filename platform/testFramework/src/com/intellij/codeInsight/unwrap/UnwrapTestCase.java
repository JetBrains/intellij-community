/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.unwrap;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class UnwrapTestCase extends LightPlatformCodeInsightTestCase {
  protected void assertUnwrapped(String codeBefore, String expectedCodeAfter) throws Exception {
    assertUnwrapped(codeBefore, expectedCodeAfter, 0);
  }

  protected void assertUnwrapped(String codeBefore, String expectedCodeAfter, final int option) throws Exception {
    configureCode(codeBefore);

    UnwrapHandler h = new UnwrapHandler() {
      @Override
      protected void selectOption(List<UnwrapHandler.MyUnwrapAction> options, Editor editor, PsiFile file) {
        if (options.isEmpty()) return;
        options.get(option).perform();
      }
    };

    h.invoke(getProject(), getEditor(), getFile());

    checkResultByText(createCode(expectedCodeAfter));
  }

  protected void assertOptions(String code, String... expectedOptions) throws IOException {
    configureCode(code);

    final List<String> actualOptions = new ArrayList<>();

    UnwrapHandler h = new UnwrapHandler() {
      @Override
      protected void selectOption(List<UnwrapHandler.MyUnwrapAction> options, Editor editor, PsiFile file) {
        for (AnAction each : options) {
          actualOptions.add(each.getTemplatePresentation().getText());
        }
      }
    };

    h.invoke(getProject(), getEditor(), getFile());

    //noinspection MisorderedAssertEqualsArguments
    assertEquals(Arrays.asList(expectedOptions), actualOptions);
  }

  protected void configureCode(final String codeBefore) throws IOException {
    configureFromFileText(getFileNameToCreate(), createCode(codeBefore));
  }

  protected String getFileNameToCreate() {
    return "A.java";
  }

  protected String createCode(String codeBefore) {
    return "public class A {\n" +
           "    void foo() {\n" +
           indentTwice(codeBefore) +
           "    }\n" +
           "}";
  }

  protected String indentTwice(String code) {
    return indent(indent(code));
  }

  protected String indent(String code) {
    String result = "";
    for (String line : StringUtil.tokenize(code, "\n")) {
      result += "    " + line + "\n";
    }
    return result;
  }
}