/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.codeStyleSettings;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType;

public class CodeSamplesCorrectnessTest extends LightPlatformCodeInsightFixtureTestCase {
  private List<CodeErrorReport> myErrorReports;
  private SettingsType[] mySettingValues;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myErrorReports = ContainerUtil.newArrayList();
    mySettingValues = SettingsType.values();
  }

  public void testAllCodeStylePreviewSamplesValid() {
    LanguageCodeStyleSettingsProvider[] providers = LanguageCodeStyleSettingsProvider.EP_NAME.getExtensions();

    for (LanguageCodeStyleSettingsProvider provider : providers) {
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
    Set<String> processedSamples = ContainerUtil.newHashSet();
    List<CodeSampleInfo> sampleInfos = ContainerUtil.newArrayList();

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
      if (type == null) return ContainerUtil.newArrayList();
      file = myFixture.configureByText(type, sample);
    }

    return PsiTreeUtil.collectElementsOfType(file, PsiErrorElement.class);
  }

  @NotNull
  private static String formReport(@NotNull List<CodeErrorReport> errorReports) {
    return StringUtil.join(errorReports, new Function<CodeErrorReport, String>() {
      @Override
      public String fun(CodeErrorReport report) {
        return report.createReport();
      }
    }, "\n");
  }
}

class CodeSampleInfo {
  public final SettingsType correspondingSetting;
  public final String codeSample;

  public CodeSampleInfo(SettingsType setting, String code) {
    correspondingSetting = setting;
    codeSample = code;
  }
}

class CodeErrorReport {
  private final Language myLang;
  private final String myCode;
  private final SettingsType mySettingsType;
  private final Collection<PsiErrorElement> myErrors;

  public CodeErrorReport(@NotNull CodeSampleInfo codeSampleInfo,
                         @NotNull Language lang,
                         @NotNull Collection<PsiErrorElement> errors) {
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