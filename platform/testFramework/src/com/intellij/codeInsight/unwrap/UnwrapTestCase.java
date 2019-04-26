// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.unwrap;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class UnwrapTestCase extends LightPlatformCodeInsightTestCase {
  protected void assertUnwrapped(String codeBefore, String expectedCodeAfter) {
    assertUnwrapped(codeBefore, expectedCodeAfter, 0);
  }

  protected void assertUnwrapped(String codeBefore, String expectedCodeAfter, final int option) {
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

  protected void assertOptions(String code, String... expectedOptions) {
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

  protected void configureCode(final String codeBefore) {
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

  protected static String indentTwice(String code) {
    return indent(code, 2);
  }

  protected static String indent(String code) {
    return indent(code, 1);
  }

  protected static String indent(String code, int times) {
    StringBuilder result = new StringBuilder();
    for (String line : StringUtil.tokenize(code, "\n")) {
      for (int i = 0; i < times; i++) result.append("    ");
      result.append(line).append('\n');
    }
    return result.toString();
  }
}