// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.codeInsight;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.search.PsiTodoSearchHelperImpl;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.psi.search.TodoPattern;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.StringJoiner;

final class TodoHighlightVisitor implements HighlightVisitor {
  @Override
  public boolean suitableForFile(@NotNull PsiFile psiFile) {
    return true;
  }

  @Override
  public boolean analyze(@NotNull PsiFile psiFile,
                         boolean updateWholeFile,
                         @NotNull HighlightInfoHolder holder,
                         @NotNull Runnable action) {
    action.run();
    // run unconditionally, because the todo API sucks and is file-level only
    highlightTodos(psiFile, psiFile.getText(), holder);
    return true;
  }

  @Override
  public void visit(@NotNull PsiElement element) {
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public @NotNull HighlightVisitor clone() {
    return new TodoHighlightVisitor();
  }

  private static void highlightTodos(@NotNull PsiFile file,
                                     @NotNull CharSequence text,
                                     @NotNull HighlightInfoHolder holder) {
    PsiTodoSearchHelper helper = PsiTodoSearchHelper.getInstance(file.getProject());
    if (helper == null || !shouldHighlightTodos(helper, file)) return;
    TodoItem[] todoItems = helper.findTodoItems(file);

    boolean isNavigationEnabled = Registry.is("todo.navigation");
    for (TodoItem todoItem : todoItems) {
      ProgressManager.checkCanceled();

      TodoPattern todoPattern = todoItem.getPattern();
      if (todoPattern == null) {
        continue;
      }

      TextRange textRange = todoItem.getTextRange();
      List<TextRange> additionalRanges = todoItem.getAdditionalTextRanges();

      String description = formatDescription(text, textRange, additionalRanges);
      String tooltip = XmlStringUtil.escapeString(StringUtil.shortenPathWithEllipsis(description, 1024)).replace("\n", "<br>");

      TextAttributes attributes = todoPattern.getAttributes().getTextAttributes();
      addTodoItem(holder, attributes, description, tooltip, textRange);
      if (!additionalRanges.isEmpty()) {
        TextAttributes attributesForAdditionalLines = attributes.clone();
        attributesForAdditionalLines.setErrorStripeColor(null);
        for (TextRange range: additionalRanges) {
          addTodoItem(holder, attributesForAdditionalLines, description, tooltip, range);
        }
      }

      if (isNavigationEnabled) {
        String wordToHighlight = todoPattern.getIndexPattern().getWordToHighlight();
        if (wordToHighlight != null) {
          int offset = Strings.indexOfIgnoreCase(text, wordToHighlight, textRange.getStartOffset(), textRange.getEndOffset());
          if (offset >= 0) {
            var info = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION)
              .range(offset, offset + wordToHighlight.length())
              .textAttributes(CodeInsightColors.INACTIVE_HYPERLINK_ATTRIBUTES)
              .createUnconditionally();
            holder.add(info);
          }
        }
      }
    }
  }

  private static @NlsSafe String formatDescription(@NotNull CharSequence text, @NotNull TextRange textRange, @NotNull List<? extends TextRange> additionalRanges) {
    StringJoiner joiner = new StringJoiner("\n");
    joiner.add(textRange.subSequence(text));
    for (TextRange additionalRange : additionalRanges) {
      joiner.add(additionalRange.subSequence(text));
    }
    return joiner.toString();
  }

  private static void addTodoItem(@NotNull HighlightInfoHolder holder,
                                  @NotNull TextAttributes attributes,
                                  @NotNull @NlsContexts.DetailedDescription String description,
                                  @NotNull @NlsContexts.Tooltip String tooltip,
                                  @NotNull TextRange range) {
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.TODO)
      .range(range)
      .textAttributes(attributes)
      .description(description)
      .escapedToolTip(tooltip)
      .createUnconditionally();
    holder.add(info);
  }

  private static boolean shouldHighlightTodos(@NotNull PsiTodoSearchHelper helper, @NotNull PsiFile file) {
    return helper instanceof PsiTodoSearchHelperImpl && ((PsiTodoSearchHelperImpl)helper).shouldHighlightInEditor(file);
  }
}