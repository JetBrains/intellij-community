// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.formatting.service.AbstractDocumentFormattingService;
import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.formatting.service.FormattingService;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.formatter.FormatterTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class FormattingServiceTest {
  private static final String EXTENSION = "txt";

  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private Project project;
  private String testName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    project = codeInsightFixture.get().getProject();
    FormatterTestUtil.assertNoProjectCodeStyle(project);
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().get().getName(), true);
  }

  @Test
  public void testCustomFormatting() throws Exception {
    registerFormattingService(new CustomFormattingService());
    doTest(null);
  }

  @Test
  public void testCustomRangeFormatting() throws Exception {
    registerFormattingService(new CustomFormattingService());
    doTest(TextRange.create(6, 11));
  }

  @Test
  public void testAsyncFormatting() throws Exception {
    registerFormattingService(new CustomAsyncFormattingService());
    doTest(null);
  }

  private void registerFormattingService(@NotNull FormattingService service) {
    FormattingService.EP_NAME.getPoint().registerExtension(service, codeInsightFixture.get().getTestRootDisposable());
  }

  private static String getTestDataDir() {
    return PathManagerEx.getTestDataPath() + File.separatorChar + "../../../platform/platform-tests/testData/codeStyle/formatter/service";
  }

  private void doTest(@Nullable TextRange textRange) throws IOException {
    String testDataDir = getTestDataDir();
    String text = FormatterTestUtil.loadFile(testDataDir, testName + "." + EXTENSION, null);
    String textAfter = FormatterTestUtil.loadFile(testDataDir, testName + "_after." + EXTENSION, null);
    PsiFile file = FormatterTestUtil.createFile(project, "before." + EXTENSION, text);
    FormatterTestUtil.checkDocument(project, file, text, textAfter, textRange);
  }

  private static class CustomFormattingService extends AbstractDocumentFormattingService {
    private static final Set<Feature> FEATURES = EnumSet.of(Feature.AD_HOC_FORMATTING,
                                                            Feature.FORMAT_FRAGMENTS);


    @Override
    public void formatDocument(@NotNull Document document,
                               @NotNull List<TextRange> formattingRanges,
                               @NotNull FormattingContext formattingContext, boolean canChangeWhiteSpaceOnly, boolean quickFormat) {
      for (TextRange range : formattingRanges) {
        document.replaceString(
          range.getStartOffset(), range.getEndOffset(),
          getFormatted(
            document.getCharsSequence().subSequence(range.getStartOffset(), range.getEndOffset())));
      }
    }

    @Override
    public @NotNull Set<Feature> getFeatures() {
      return FEATURES;
    }

    @Override
    public boolean canFormat(@NotNull PsiFile file) {
      return true;
    }
  }

  private static @NotNull String getFormatted(@NotNull CharSequence chars) {
    StringBuilder replacement = new StringBuilder();
    for (int i = 0; i < chars.length(); i ++) {
      char c = chars.charAt(i);
      replacement.append(c == ' ' || c == '\n' || c == '\r' ? c : '*');
    }
    return replacement.toString();
  }

  private static class CustomAsyncFormattingService extends AsyncDocumentFormattingService {

    @Override
    protected @Nullable FormattingTask createFormattingTask(@NotNull AsyncFormattingRequest formattingRequest) {
      return new FormattingTask() {
        @Override
        public boolean cancel() {
          return false;
        }

        @Override
        public void run() {
          String documentText = formattingRequest.getDocumentText();
          new Thread(() -> {
            String formatted = getFormatted(documentText);
            formattingRequest.onTextReady(formatted);
          }, "AsyncFormat").start();
        }
      };
    }

    @Override
    protected @NotNull String getNotificationGroupId() {
      return "Test group";
    }

    @Override
    protected @NotNull String getName() {
      return "AsyncFormat";
    }

    @Override
    public @NotNull Set<Feature> getFeatures() {
      return EnumSet.of(Feature.AD_HOC_FORMATTING,
                        Feature.FORMAT_FRAGMENTS);
    }

    @Override
    public boolean canFormat(@NotNull PsiFile file) {
      return true;
    }
  }
}
