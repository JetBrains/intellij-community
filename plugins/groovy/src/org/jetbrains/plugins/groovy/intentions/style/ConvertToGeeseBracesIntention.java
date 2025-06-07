// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.style;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.formatter.GeeseUtil;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Max Medvedev
 */
public final class ConvertToGeeseBracesIntention extends GrPsiUpdateIntention {
  private static final Logger LOG = Logger.getInstance(ConvertToGeeseBracesIntention.class);

  private static final PsiElementPredicate MY_PREDICATE = new PsiElementPredicate() {
    @Override
    public boolean satisfiedBy(@NotNull PsiElement element) {
      if (element.getLanguage() != GroovyLanguage.INSTANCE) return false;
      if (!CodeStyleSettingsManager.getInstance(element.getProject()).getCurrentSettings()
        .getCustomSettings(GroovyCodeStyleSettings.class).USE_FLYING_GEESE_BRACES) {
        return false;
      }

      if (PsiImplUtil.isWhiteSpaceOrNls(element)) {
        element = PsiTreeUtil.prevLeaf(element);
      }

      if (!GeeseUtil.isClosureRBrace(element) || !GeeseUtil.isClosureContainLF(element)) return false;

      TextRange range = findRange(element);

      return StringUtil.contains(element.getContainingFile().getText(), range.getStartOffset(), range.getEndOffset(), '\n');
    }
  };

  private static @Nullable PsiElement getPrev(PsiElement element) {
    PsiElement prev = PsiUtil.getPreviousNonWhitespaceToken(element);
    if (prev != null && prev.getNode().getElementType() == GroovyTokenTypes.mNLS) {
      prev = PsiUtil.getPreviousNonWhitespaceToken(prev);
    }
    return prev;
  }

  private static @Nullable PsiElement getNext(PsiElement element) {
    PsiElement next = GeeseUtil.getNextNonWhitespaceToken(element);
    if (next != null && next.getNode().getElementType() == GroovyTokenTypes.mNLS) next = GeeseUtil.getNextNonWhitespaceToken(next);
    return next;
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    if (PsiImplUtil.isWhiteSpaceOrNls(element)) {
      element = PsiTreeUtil.prevLeaf(element);
    }
    LOG.assertTrue(GeeseUtil.isClosureRBrace(element) && GeeseUtil.isClosureContainLF(element));

    PsiFile file = element.getContainingFile();
    Document document = file.getViewProvider().getDocument();

    TextRange textRange = findRange(element);
    int startOffset = textRange.getStartOffset();
    int endOffset = textRange.getEndOffset();

    RangeMarker rangeMarker = document.createRangeMarker(textRange);

    String text = document.getText();
    for (int i = endOffset - 1; i >= startOffset; i--) {
      if (text.charAt(i) == '\n') document.deleteString(i, i + 1);
    }

    CodeStyleManager.getInstance(context.project()).reformatText(file, rangeMarker.getStartOffset(), rangeMarker.getEndOffset());
  }

  private static TextRange findRange(PsiElement element) {
    PsiElement first = null;
    PsiElement last = null;
    for (PsiElement cur = element; GeeseUtil.isClosureRBrace(cur) && GeeseUtil.isClosureContainLF(cur); cur = getNext(cur)) {
      last = cur;
    }

    for (PsiElement cur = element; GeeseUtil.isClosureRBrace(cur) && GeeseUtil.isClosureContainLF(cur); cur = getPrev(cur)) {
      first = cur;
    }

    LOG.assertTrue(first != null);
    LOG.assertTrue(last != null);


    return new TextRange(first.getTextRange().getStartOffset(), last.getTextRange().getEndOffset());
  }


  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return MY_PREDICATE;
  }
}
