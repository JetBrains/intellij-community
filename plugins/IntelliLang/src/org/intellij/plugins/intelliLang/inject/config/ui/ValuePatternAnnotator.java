// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.inject.config.ui;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.intellij.plugins.intelliLang.IntelliLangBundle;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validates the {@link ValuePatternField}: each branch of the regex should contain exactly one capturing group,
 * the group is the text range the language is injected into.
 */
@SuppressWarnings("SplitModeApiUsage") // lightweight check of the Value-Pattern settings field, no resolve
final class ValuePatternAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (!(element instanceof PsiFile file) || file.getCopyableUserData(ValuePatternField.KEY) != Boolean.TRUE) return;
    String pattern = file.getText();
    if (pattern.isEmpty() || !isValid(pattern)) return; // syntax errors are reported by the RegExp highlighting
    for (TextRange branch : splitTopLevelBranches(pattern)) {
      String text = branch.substring(pattern);
      if (isValid(text) && Pattern.compile(text).matcher("").groupCount() != 1) {
        holder.newAnnotation(HighlightSeverity.WARNING, IntelliLangBundle.message("annotation.message.the.pattern")).range(branch).create();
      }
    }
  }

  private static boolean isValid(@NotNull String pattern) {
    try {
      Pattern.compile(pattern);
      return true;
    }
    catch (PatternSyntaxException e) {
      return false;
    }
  }

  private static @NotNull List<TextRange> splitTopLevelBranches(@NotNull String pattern) {
    List<TextRange> branches = new ArrayList<>();
    int groupDepth = 0;
    int classDepth = 0;
    int start = 0;
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      if (c == '\\') {
        if (i + 1 < pattern.length() && pattern.charAt(i + 1) == 'Q') {
          int end = pattern.indexOf("\\E", i + 2);
          i = end < 0 ? pattern.length() : end + 1;
        }
        else {
          i++;
        }
      }
      else if (c == '[') {
        classDepth++;
      }
      else if (c == ']' && classDepth > 0) {
        classDepth--;
      }
      else if (classDepth == 0) {
        if (c == '(') {
          groupDepth++;
        }
        else if (c == ')') {
          groupDepth--;
        }
        else if (c == '|' && groupDepth == 0) {
          branches.add(new TextRange(start, i));
          start = i + 1;
        }
      }
    }
    branches.add(new TextRange(start, pattern.length()));
    return branches;
  }
}
