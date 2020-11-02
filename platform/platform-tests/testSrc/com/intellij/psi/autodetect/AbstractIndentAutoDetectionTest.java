// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.autodetect;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.autodetect.*;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.util.List;

public abstract class AbstractIndentAutoDetectionTest extends LightPlatformCodeInsightTestCase {

  @NotNull
  protected abstract String getFileNameWithExtension();

  @Override
  @NotNull
  protected abstract String getTestDataPath();

  protected void doTestMaxUsedIndent(int indentExpected, int timesUsedExpected) {
    IndentUsageInfo maxIndentExpected = new IndentUsageInfo(indentExpected, timesUsedExpected);
    IndentUsageInfo indentInfo = getMaxUsedIndentInfo();
    Assert.assertEquals("Indent size mismatch", maxIndentExpected.getIndentSize(), indentInfo.getIndentSize());
    Assert.assertEquals("Indent size usage number mismatch", maxIndentExpected.getTimesUsed(), indentInfo.getTimesUsed());
  }

  protected void doTestMaxUsedIndent(int indentExpected) {
    IndentUsageInfo indentInfo = getMaxUsedIndentInfo();
    Assert.assertEquals("Indent size mismatch", indentExpected, indentInfo.getIndentSize());
  }

  protected void doTestTabsUsed() {
    doTestTabsUsed(null);
  }

  protected void doTestIndentSize(int expectedIndent) {
    configureByFile(getFileNameWithExtension());
    doTestIndentSize(null, expectedIndent);
  }
  
  protected void doTestIndentSizeFromText(String text, @Nullable CommonCodeStyleSettings.IndentOptions options,  int expectedIndent) {
    configureFromFileText(getFileNameWithExtension(), text);
    doTestIndentSize(options, expectedIndent);
  }

  protected void doTestTabsUsed(@Nullable CommonCodeStyleSettings.IndentOptions defaultIndentOptions) {
    configureByFile(getFileNameWithExtension());

    if (defaultIndentOptions != null) {
      setIndentOptions(defaultIndentOptions);
    }

    CommonCodeStyleSettings.IndentOptions options = detectIndentOptions(getFile());
    Assert.assertTrue("Tab usage not detected", options.USE_TAB_CHARACTER);
  }

  private void doTestIndentSize(@Nullable CommonCodeStyleSettings.IndentOptions defaultIndentOptions, int expectedIndent) {
    if (defaultIndentOptions != null) {
      setIndentOptions(defaultIndentOptions);
    }

    CommonCodeStyleSettings.IndentOptions options = detectIndentOptions(getFile());
    Assert.assertFalse("Tab usage detected: ", options.USE_TAB_CHARACTER);
    Assert.assertEquals("Indent mismatch", expectedIndent, options.INDENT_SIZE);
  }

  private void setIndentOptions(@NotNull CommonCodeStyleSettings.IndentOptions defaultIndentOptions) {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptions(getFile().getFileType());
    indentOptions.copyFrom(defaultIndentOptions);
  }
  
  @NotNull
  private IndentUsageInfo getMaxUsedIndentInfo() {
    configureByFile(getFileNameWithExtension());
    Document document = getDocument(getFile());

    FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(getFile());
    Assert.assertNotNull(builder);

    FormattingModel model =
      builder.createModel(FormattingContext.create(getFile(), CodeStyle.getSettings(getProject())));
    List<LineIndentInfo> lines = new FormatterBasedLineIndentInfoBuilder(document, model.getRootBlock(), null).build();

    IndentUsageStatistics statistics = new IndentUsageStatisticsImpl(lines);
    return statistics.getKMostUsedIndentInfo(0);
  }

  @NotNull
  public static CommonCodeStyleSettings.IndentOptions detectIndentOptions(PsiFile file) {
    IndentOptionsDetector detector = new IndentOptionsDetectorImpl(file);
    return detector.getIndentOptions();
  }
}
