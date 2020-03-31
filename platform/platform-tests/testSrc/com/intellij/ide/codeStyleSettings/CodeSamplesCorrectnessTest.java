// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.codeStyleSettings;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.*;

import static com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType;

public class CodeSamplesCorrectnessTest extends BasePlatformTestCase {
  private List<CodeErrorReport> myErrorReports;
  private SettingsType[] mySettingValues;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myErrorReports = new ArrayList<>();
    mySettingValues = SettingsType.values();
  }

  public void testNonPhysicalFiles() {
    LanguageCodeStyleSettingsProvider[] providers = LanguageCodeStyleSettingsProvider.EP_NAME.getExtensions();
    for (LanguageCodeStyleSettingsProvider provider : providers) {
      if (isSql(provider.getLanguage())) continue; // SQL a is special case, has its own tests
      List<CodeSampleInfo> samplesToTest = getSamplesToTest(provider);
      for (CodeSampleInfo sampleInfo : samplesToTest) {
        PsiFile file = provider.createFileFromText(getProject(), sampleInfo.codeSample);
        if (file != null) {
          assertFalse(provider.getClass() + " must not create a physical file with psi events enabled", file.isPhysical());
        }
      }
    }
  }

  public void testAllCodeStylePreviewSamplesValid() {
    LanguageCodeStyleSettingsProvider[] providers = LanguageCodeStyleSettingsProvider.EP_NAME.getExtensions();
    for (LanguageCodeStyleSettingsProvider provider : providers) {
      if (isSql(provider.getLanguage())) continue; // SQL a is special case, has its own tests
      List<CodeSampleInfo> samplesToTest = getSamplesToTest(provider);
      processCodeSamples(provider, samplesToTest);
    }

    Assert.assertTrue(formReport(myErrorReports), myErrorReports.isEmpty());
  }

  private void processCodeSamples(@NotNull LanguageCodeStyleSettingsProvider provider, @NotNull List<CodeSampleInfo> samples) {
    for (CodeSampleInfo sample : samples) {
      Collection<PsiErrorElement> errorReports = getErrorsForText(provider, sample.codeSample);
      if (errorReports.isEmpty()) {
        continue;
      }
      CodeErrorReport report = new CodeErrorReport(sample, provider.getLanguage(), errorReports);
      myErrorReports.add(report);
    }
  }

  private List<CodeSampleInfo> getSamplesToTest(@NotNull LanguageCodeStyleSettingsProvider provider) {
    Set<String> processedSamples = new HashSet<>();
    List<CodeSampleInfo> sampleInfos = new ArrayList<>();

    for (SettingsType setting : mySettingValues) {
      String sample = provider.getCodeSample(setting);
      if (StringUtil.isEmpty(sample) || processedSamples.contains(sample)) {
        continue;
      }

      processedSamples.add(sample);
      sampleInfos.add(new CodeSampleInfo(setting, sample));
    }

    return sampleInfos;
  }

  @NotNull
  private Collection<PsiErrorElement> getErrorsForText(@NotNull LanguageCodeStyleSettingsProvider provider, @NotNull String sample) {
    PsiFile file = provider.createFileFromText(getProject(), sample);
    if (file == null) {
      Language language = provider.getLanguage();
      LanguageFileType type = language.getAssociatedFileType();
      if (type == null) return new ArrayList<>();
      file = myFixture.configureByText(type, sample);
    }

    return PsiTreeUtil.collectElementsOfType(file, PsiErrorElement.class);
  }

  @NotNull
  private static String formReport(@NotNull List<CodeErrorReport> errorReports) {
    return StringUtil.join(errorReports, report -> report.createReport(), "\n");
  }


  private static boolean isSql(@NotNull Language lang) {
    return "SQL".equals(lang.getID());
  }
}

class CodeSampleInfo {
  public final SettingsType correspondingSetting;
  public final String codeSample;

  CodeSampleInfo(SettingsType setting, String code) {
    correspondingSetting = setting;
    codeSample = code;
  }
}

class CodeErrorReport {
  private final Language myLang;
  private final String myCode;
  private final SettingsType mySettingsType;
  private final Collection<? extends PsiErrorElement> myErrors;

  CodeErrorReport(@NotNull CodeSampleInfo codeSampleInfo,
                         @NotNull Language lang,
                         @NotNull Collection<? extends PsiErrorElement> errors) {
    myLang = lang;
    myCode = codeSampleInfo.codeSample;
    mySettingsType = codeSampleInfo.correspondingSetting;
    myErrors = errors;
  }

  public String createReport() {
    StringBuilder builder = new StringBuilder("\n\n\n");

    builder.append("Language: ")
      .append(myLang.getDisplayName())
      .append(". Setting: ")
      .append(mySettingsType)
      .append(". Errors found: ")
      .append(myErrors.size())
      .append('\n');

    for (PsiErrorElement error : myErrors) {
      builder.append("   ")
        .append(error.getErrorDescription())
        .append(". Previous sibling: ")
        .append(error.getPrevSibling())
        .append('\n');
    }

    builder.append("\nCode sample:\n")
      .append(myCode)
      .append('\n');

    return builder.toString();
  }
}