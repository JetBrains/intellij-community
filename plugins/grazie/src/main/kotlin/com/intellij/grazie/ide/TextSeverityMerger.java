package com.intellij.grazie.ide;

import com.intellij.codeInsight.daemon.impl.StatusItemMerger;
import com.intellij.grazie.GrazieBundle;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.markup.SeverityStatusItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.grazie.ide.TextProblemSeverities.*;

class TextSeverityMerger extends StatusItemMerger {
  @Override
  public @Nullable SeverityStatusItem mergeItems(@NotNull SeverityStatusItem higher, @NotNull SeverityStatusItem lower) {
    HighlightSeverity s1 = higher.getSeverity();
    HighlightSeverity s2 = lower.getSeverity();
    int sum = higher.getProblemCount() + lower.getProblemCount();
    if (s1 == GRAMMAR_ERROR && "TYPO".equals(s2.getName())) {
      return new SeverityStatusItem(s1, higher.getIcon(), sum, GrazieBundle.message("text.error.severity.count", sum));
    }

    if (s1 == STYLE_ERROR && (s2 == STYLE_WARNING || s2 == STYLE_SUGGESTION) ||
        s1 == STYLE_WARNING && s2 == STYLE_SUGGESTION) {
      return new SeverityStatusItem(s1, higher.getIcon(), sum, s1.getCountMessage(sum));
    }
    
    return null;
  }
}
