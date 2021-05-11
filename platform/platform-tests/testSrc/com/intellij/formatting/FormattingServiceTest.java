// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.formatting.service.AbstractDocumentFormattingService;
import com.intellij.formatting.service.FormattingService;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.formatter.FormatterTestCase;
import org.jetbrains.annotations.NotNull;

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
        CharSequence chars = document.getCharsSequence().subSequence(range.getStartOffset(), range.getEndOffset());
        StringBuilder replacement = new StringBuilder();
        for (int i = 0; i < chars.length(); i ++) {
          char c = chars.charAt(i);
          replacement.append(c == ' ' || c == '\n' || c == '\r' ? c : '*');
        }
        document.replaceString(range.getStartOffset(), range.getEndOffset(), replacement);
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
}
