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
      protected void selectOption(List<AnAction> options, Editor editor, PsiFile file) {
        if (options.isEmpty()) return;
        options.get(option).actionPerformed(null);
      }
    };

    h.invoke(getProject(), getEditor(), getFile());

    checkResultByText(createCode(expectedCodeAfter));
  }

  protected void assertOptions(String code, String... expectedOptions) throws IOException {
    configureCode(code);

    final List<String> actualOptions = new ArrayList<String>();

    UnwrapHandler h = new UnwrapHandler() {
      @Override
      protected void selectOption(List<AnAction> options, Editor editor, PsiFile file) {
        for (AnAction each : options) {
          actualOptions.add(each.getTemplatePresentation().getText());
        }
      }
    };

    h.invoke(getProject(), getEditor(), getFile());

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