// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.formatting.service.AbstractDocumentFormattingService;
import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.formatting.service.FormattingService;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.formatter.FormatterTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class FormattingServiceTest extends FormatterTestCase {

  public void testCustomFormatting() throws Exception {
    FormattingService.EP_NAME.getPoint().registerExtension(new CustomFormattingService(), getTestRootDisposable());
    doTest();
  }

  public void testCustomRangeFormatting() throws Exception {
    FormattingService.EP_NAME.getPoint().registerExtension(new CustomFormattingService(), getTestRootDisposable());
    myTextRange = TextRange.create(6,11);
    doTest();
  }

  public void testAsyncFormatting() throws Exception {
    FormattingService.EP_NAME.getPoint().registerExtension(new CustomAsyncFormattingService(), getTestRootDisposable());
    doTest();
  }

  @Override
  protected String getBasePath() {
    return "../../../platform/platform-tests/testData/codeStyle/formatter/service";
  }

  @Override
  protected String getFileExtension() {
    return "txt";
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
