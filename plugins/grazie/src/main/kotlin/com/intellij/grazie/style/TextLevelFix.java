package com.intellij.grazie.style;

import com.intellij.codeInsight.intention.CustomizableIntentionAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.grazie.utils.HighlightingUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.text.StringOperation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TextLevelFix extends LocalQuickFixAndIntentionActionOnPsiElement implements CustomizableIntentionAction {
  private final long stamp;
  @SafeFieldForPreview private final List<StringOperation> allChanges;
  private final String text;

  public TextLevelFix(@NotNull PsiElement scope, String text, List<StringOperation> allChanges) {
    super(scope);
    this.text = text;
    this.allChanges = allChanges;
    stamp = scope.getContainingFile().getModificationStamp();
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile psiFile, @Nullable Editor editor,
                     @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    HighlightingUtil.applyTextChanges(psiFile.getViewProvider().getDocument(), allChanges);
  }

  @Override
  public boolean isAvailable(@NotNull Project project, @NotNull PsiFile psiFile, @Nullable Editor editor,
                             @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    return stamp == psiFile.getModificationStamp();
  }

  @Override
  public @IntentionName @NotNull String getText() {
    return text;
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isShowSubmenu() {
    return false;
  }

  @Override
  public @NotNull List<RangeToHighlight> getRangesToHighlight(@NotNull Editor editor, @NotNull PsiFile file) {
    return allChanges.stream()
      .map(r -> r.getRange())
      .map(r -> r.getLength() > 0 || file.getTextLength() == 0 ? r :
                r.getEndOffset() < file.getTextLength() ? new TextRange(r.getStartOffset(), r.getEndOffset() + 1) :
                new TextRange(r.getStartOffset() - 1, r.getEndOffset()))
      .filter(r -> r.getLength() > 0)
      .map(r -> new RangeToHighlight(file, r, EditorColors.SEARCH_RESULT_ATTRIBUTES))
      .toList();
  }
}
