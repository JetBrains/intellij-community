// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.codeStyleSettings;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.assertj.core.description.LazyTextDescription;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class CodeSamplesCorrectnessTest extends BasePlatformTestCase {
  private List<CodeErrorReport> errorReports;
  private SettingsType[] settingValues;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    errorReports = new ArrayList<>();
    settingValues = SettingsType.values();
  }

  public void testNonPhysicalFiles() {
    for (LanguageCodeStyleSettingsProvider provider : LanguageCodeStyleSettingsProvider.getAllProviders()) {
      // SQL is a special case, has its own tests
      if (isSql(provider.getLanguage())) {
        continue;
      }

      List<CodeSampleInfo> samplesToTest = getSamplesToTest(provider);
      for (CodeSampleInfo sampleInfo : samplesToTest) {
        PsiFile file = provider.createFileFromText(getProject(), sampleInfo.codeSample);
        if (file != null) {
          assertThat(file.isPhysical())
            .describedAs(provider.getClass() + " must not create a physical file with psi events enabled")
            .isFalse();
        }
      }
    }
  }

  public void testAllCodeStylePreviewSamplesValid() {
    for (LanguageCodeStyleSettingsProvider provider : LanguageCodeStyleSettingsProvider.getAllProviders()) {
      if (isSql(provider.getLanguage())) {
        // SQL is a special case, has its own tests
        continue;
      }

      processCodeSamples(provider, getSamplesToTest(provider));
    }

    assertThat(errorReports).describedAs(new LazyTextDescription(() -> formReport(errorReports))).isEmpty();
  }

  private void processCodeSamples(@NotNull LanguageCodeStyleSettingsProvider provider, @NotNull List<CodeSampleInfo> samples) {
    for (CodeSampleInfo sample : samples) {
      Collection<PsiErrorElement> errorReports = getErrorsForText(provider, sample.codeSample);
      if (errorReports.isEmpty()) {
        continue;
      }

      this.errorReports.add(new CodeErrorReport(sample, provider.getLanguage(), errorReports));
    }
  }

  private List<CodeSampleInfo> getSamplesToTest(@NotNull LanguageCodeStyleSettingsProvider provider) {
    Set<String> processedSamples = new HashSet<>();
    List<CodeSampleInfo> sampleInfos = new ArrayList<>();

    for (SettingsType setting : settingValues) {
      String sample = provider.getCodeSample(setting);
      if (StringUtil.isEmpty(sample) || processedSamples.contains(sample)) {
        continue;
      }

      processedSamples.add(sample);
      sampleInfos.add(new CodeSampleInfo(setting, sample));
    }

    return sampleInfos;
  }

  private @NotNull Collection<PsiErrorElement> getErrorsForText(@NotNull LanguageCodeStyleSettingsProvider provider,
                                                                @NotNull String sample) {
    PsiFile file = provider.createFileFromText(getProject(), sample);
    if (file == null) {
      Language language = provider.getLanguage();
      LanguageFileType type = language.getAssociatedFileType();
      if (type == null) return new ArrayList<>();
      file = myFixture.configureByText(type, sample);
    }

    return PsiTreeUtil.collectElementsOfType(file, PsiErrorElement.class);
  }

  private static @NotNull String formReport(@NotNull List<CodeErrorReport> errorReports) {
    return StringUtil.join(errorReports, CodeErrorReport::createReport, "\n");
  }


  private static boolean isSql(@NotNull Language lang) {
    return "SQL".equals(lang.getID());
  }
}

final class CodeSampleInfo {
  public final SettingsType correspondingSetting;
  public final String codeSample;

  CodeSampleInfo(SettingsType setting, String code) {
    correspondingSetting = setting;
    codeSample = code;
  }
}

final class CodeErrorReport {
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