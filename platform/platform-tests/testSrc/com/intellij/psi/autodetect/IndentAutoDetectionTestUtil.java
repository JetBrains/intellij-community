// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.autodetect;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.autodetect.FormatterBasedLineIndentInfoBuilder;
import com.intellij.psi.codeStyle.autodetect.IndentOptionsDetectorImpl;
import com.intellij.psi.codeStyle.autodetect.IndentUsageInfo;
import com.intellij.psi.codeStyle.autodetect.IndentUsageStatistics;
import com.intellij.psi.codeStyle.autodetect.IndentUsageStatisticsImpl;
import com.intellij.psi.codeStyle.autodetect.LineIndentInfo;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the indent that {@link IndentOptionsDetectorImpl} detects in a file.
 * <p>
 * The methods with a {@link CodeInsightTestFixture} configure the file themselves.
 * {@code fileName} is the name of the test data file to configure, or the name of the file to create from a text.
 * The {@code check} methods take a file that the test already configured, so {@link AbstractIndentAutoDetectionTest} also calls them.
 * The methods read the PSI and the document in read actions, so a test can call them off the EDT.
 */
@TestOnly
public final class IndentAutoDetectionTestUtil {
  private IndentAutoDetectionTestUtil() {
  }

  /**
   * Configures the test data file {@code fileName}, and checks the most used indent of the formatter blocks and the number of its uses.
   */
  public static void doTestMaxUsedIndent(@NotNull CodeInsightTestFixture fixture, @NotNull String fileName,
                                         int indentExpected, int timesUsedExpected) {
    fixture.configureByFile(fileName);
    checkMaxUsedIndent(fixture.getProject(), fixture.getFile(), indentExpected, timesUsedExpected);
  }

  /**
   * Configures the test data file {@code fileName}, and checks the most used indent of the formatter blocks.
   */
  public static void doTestMaxUsedIndent(@NotNull CodeInsightTestFixture fixture, @NotNull String fileName, int indentExpected) {
    fixture.configureByFile(fileName);
    checkMaxUsedIndent(fixture.getProject(), fixture.getFile(), indentExpected);
  }

  /**
   * Configures the test data file {@code fileName}, and checks that the detected indent options use tabs.
   */
  public static void doTestTabsUsed(@NotNull CodeInsightTestFixture fixture, @NotNull String fileName) {
    doTestTabsUsed(fixture, fileName, null);
  }

  /**
   * Configures the test data file {@code fileName}, and checks that the detected indent options use tabs.
   * When {@code defaultIndentOptions} is not {@code null}, the indent options of the file type get its values before the detection.
   */
  public static void doTestTabsUsed(@NotNull CodeInsightTestFixture fixture, @NotNull String fileName,
                                    @Nullable CommonCodeStyleSettings.IndentOptions defaultIndentOptions) {
    fixture.configureByFile(fileName);
    checkTabsUsed(fixture.getProject(), fixture.getFile(), fixture.getEditor().getDocument(), defaultIndentOptions);
  }

  /**
   * Configures the test data file {@code fileName}, and checks that the detected indent options use spaces and {@code expectedIndent}.
   */
  public static void doTestIndentSize(@NotNull CodeInsightTestFixture fixture, @NotNull String fileName, int expectedIndent) {
    fixture.configureByFile(fileName);
    checkIndentSize(fixture.getProject(), fixture.getFile(), fixture.getEditor().getDocument(), null, expectedIndent);
  }

  /**
   * Configures {@code text} in a file named {@code fileName}, and checks that the detected indent options use spaces and {@code expectedIndent}.
   * When {@code options} is not {@code null}, the indent options of the file type get its values before the detection.
   */
  public static void doTestIndentSizeFromText(@NotNull CodeInsightTestFixture fixture, @NotNull String fileName, @NotNull String text,
                                              @Nullable CommonCodeStyleSettings.IndentOptions options, int expectedIndent) {
    fixture.configureByText(fileName, text);
    checkIndentSize(fixture.getProject(), fixture.getFile(), fixture.getEditor().getDocument(), options, expectedIndent);
  }

  /**
   * Checks the most used indent of the formatter blocks of {@code file} and the number of its uses.
   */
  public static void checkMaxUsedIndent(@NotNull Project project, @NotNull PsiFile file, int indentExpected, int timesUsedExpected) {
    IndentUsageInfo indentInfo = getMaxUsedIndentInfo(project, file);
    assertEquals(indentExpected, indentInfo.getIndentSize(), "Indent size mismatch");
    assertEquals(timesUsedExpected, indentInfo.getTimesUsed(), "Indent size usage number mismatch");
  }

  /**
   * Checks the most used indent of the formatter blocks of {@code file}.
   */
  public static void checkMaxUsedIndent(@NotNull Project project, @NotNull PsiFile file, int indentExpected) {
    IndentUsageInfo indentInfo = getMaxUsedIndentInfo(project, file);
    assertEquals(indentExpected, indentInfo.getIndentSize(), "Indent size mismatch");
  }

  /**
   * Checks that the indent options detected in {@code file} use tabs.
   * When {@code defaultIndentOptions} is not {@code null}, the indent options of the file type get its values before the detection.
   */
  public static void checkTabsUsed(@NotNull Project project, @NotNull PsiFile file, @NotNull Document document,
                                   @Nullable CommonCodeStyleSettings.IndentOptions defaultIndentOptions) {
    if (defaultIndentOptions != null) {
      setIndentOptions(project, file, defaultIndentOptions);
    }
    CommonCodeStyleSettings.IndentOptions options = detectIndentOptions(project, file.getVirtualFile(), document);
    assertTrue(options.USE_TAB_CHARACTER, "Tab usage not detected");
  }

  /**
   * Checks that the indent options detected in {@code file} use spaces and {@code expectedIndent}.
   * When {@code defaultIndentOptions} is not {@code null}, the indent options of the file type get its values before the detection.
   */
  public static void checkIndentSize(@NotNull Project project, @NotNull PsiFile file, @NotNull Document document,
                                     @Nullable CommonCodeStyleSettings.IndentOptions defaultIndentOptions, int expectedIndent) {
    if (defaultIndentOptions != null) {
      setIndentOptions(project, file, defaultIndentOptions);
    }
    CommonCodeStyleSettings.IndentOptions options = detectIndentOptions(project, file.getVirtualFile(), document);
    assertFalse(options.USE_TAB_CHARACTER, "Tab usage detected: ");
    assertEquals(expectedIndent, options.INDENT_SIZE, "Indent mismatch");
  }

  /**
   * Detects the indent options of {@code file} in a read action.
   */
  public static @NotNull CommonCodeStyleSettings.IndentOptions detectIndentOptions(@NotNull Project project,
                                                                                   @NotNull VirtualFile file,
                                                                                   @NotNull Document document) {
    return ReadAction.computeBlocking(
      () -> new IndentOptionsDetectorImpl(project, file, document, new EmptyProgressIndicator()).getIndentOptions());
  }

  private static void setIndentOptions(@NotNull Project project, @NotNull PsiFile file,
                                       @NotNull CommonCodeStyleSettings.IndentOptions defaultIndentOptions) {
    CodeStyleSettings settings = CodeStyle.getSettings(project);
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptions(file.getFileType());
    indentOptions.copyFrom(defaultIndentOptions);
  }

  private static @NotNull IndentUsageInfo getMaxUsedIndentInfo(@NotNull Project project, @NotNull PsiFile file) {
    return ReadAction.computeBlocking(() -> {
      Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      assertNotNull(document);

      FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);
      assertNotNull(builder);

      FormattingModel model = builder.createModel(FormattingContext.create(file, CodeStyle.getSettings(project)));
      List<LineIndentInfo> lines = new FormatterBasedLineIndentInfoBuilder(document, model.getRootBlock(), null).build();

      IndentUsageStatistics statistics = new IndentUsageStatisticsImpl(lines);
      return statistics.getKMostUsedIndentInfo(0);
    });
  }
}
