// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.unwrap;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unwrap checks for a JUnit 5 test.
 * This is the replacement of {@link UnwrapTestCase}.
 */
@TestOnly
public final class UnwrapTestUtil {
  private UnwrapTestUtil() {
  }

  /**
   * Wraps {@code codeBefore} in the method {@code foo} of the Java class {@code A}, then runs
   * {@link #assertUnwrapped(CodeInsightTestFixture, String, String, String, int)} for the file {@code A.java}.
   * Call it from any thread.
   */
  public static void assertUnwrapped(@NotNull CodeInsightTestFixture fixture,
                                     @NotNull String codeBefore,
                                     @NotNull String expectedCodeAfter,
                                     int option) {
    assertUnwrapped(fixture, "A.java", createCode(codeBefore), createCode(expectedCodeAfter), option);
  }

  /**
   * Configures {@code fileName} with {@code fileTextBefore}, runs the unwrap option with the index {@code option},
   * and checks that the file text is {@code expectedFileTextAfter}.
   * When there is no unwrap option, the file text stays the same.
   * Call it from any thread.
   */
  public static void assertUnwrapped(@NotNull CodeInsightTestFixture fixture,
                                     @NotNull String fileName,
                                     @NotNull String fileTextBefore,
                                     @NotNull String expectedFileTextAfter,
                                     int option) {
    fixture.configureByText(fileName, fileTextBefore);

    UnwrapHandler h = new UnwrapHandler() {
      @Override
      protected void selectOption(List<? extends MyUnwrapAction> options, Editor editor, PsiFile file) {
        if (options.isEmpty()) return;
        options.get(option).perform();
      }
    };

    invoke(fixture, h);

    fixture.checkResult(expectedFileTextAfter);
  }

  /**
   * Wraps {@code code} in the method {@code foo} of the Java class {@code A}, then runs
   * {@link #assertOptions(CodeInsightTestFixture, String, String, List)} for the file {@code A.java}.
   * Call it from any thread.
   */
  public static void assertOptions(@NotNull CodeInsightTestFixture fixture, @NotNull String code, String @NotNull ... expectedOptions) {
    assertOptions(fixture, "A.java", createCode(code), Arrays.asList(expectedOptions));
  }

  /**
   * Configures {@code fileName} with {@code fileText}, and checks that the texts of the unwrap options are {@code expectedOptions}.
   * Call it from any thread.
   */
  public static void assertOptions(@NotNull CodeInsightTestFixture fixture,
                                   @NotNull String fileName,
                                   @NotNull String fileText,
                                   @NotNull List<String> expectedOptions) {
    fixture.configureByText(fileName, fileText);

    final List<String> actualOptions = new ArrayList<>();

    UnwrapHandler h = new UnwrapHandler() {
      @Override
      protected void selectOption(List<? extends MyUnwrapAction> options, Editor editor, PsiFile file) {
        for (AnAction each : options) {
          actualOptions.add(each.getTemplatePresentation().getText());
        }
      }
    };

    invoke(fixture, h);

    assertEquals(expectedOptions, actualOptions);
  }

  private static void invoke(@NotNull CodeInsightTestFixture fixture, @NotNull UnwrapHandler handler) {
    Project project = fixture.getProject();
    Editor editor = fixture.getEditor();
    PsiFile file = fixture.getFile();
    EdtTestUtil.runInEdtAndWait(() -> handler.invoke(project, editor, file));
  }

  /**
   * Returns the text of the Java class {@code A} with {@code codeBefore} in its method {@code foo}.
   * Call it from any thread.
   */
  public static @NotNull String createCode(@NotNull String codeBefore) {
    return "public class A {\n" +
           "    void foo() {\n" +
           indentTwice(codeBefore) +
           "    }\n" +
           "}";
  }

  /**
   * Indents each line of {@code code} two times. See {@link #indent(String, int)}.
   */
  public static @NotNull String indentTwice(@NotNull String code) {
    return indent(code, 2);
  }

  /**
   * Indents each line of {@code code} one time. See {@link #indent(String, int)}.
   */
  public static @NotNull String indent(@NotNull String code) {
    return indent(code, 1);
  }

  /**
   * Adds {@code times} indents of four spaces to each line of {@code code}, and ends each line with a line break.
   * Call it from any thread.
   */
  public static @NotNull String indent(@NotNull String code, int times) {
    StringBuilder result = new StringBuilder();
    for (String line : StringUtil.tokenize(code, "\n")) {
      result.append("    ".repeat(times));
      result.append(line).append('\n');
    }
    return result.toString();
  }
}
