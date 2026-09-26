// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.style;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
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
public final class ConvertFromGeeseBracesIntention extends GrPsiUpdateIntention {
  private static final Logger LOG = Logger.getInstance(ConvertFromGeeseBracesIntention.class);

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

      if (!GeeseUtil.isClosureRBrace(element)) return false;

      String text = element.getContainingFile().getText();

      PsiElement first = element;
      PsiElement last = element;
      for (PsiElement cur = getNext(element); GeeseUtil.isClosureRBrace(cur); cur = getNext(cur)) {
        if (!StringUtil.contains(text, last.getTextRange().getEndOffset(), cur.getTextRange().getStartOffset(), '\n')) return true;
        last = cur;
      }

      for (PsiElement cur = getPrev(element); GeeseUtil.isClosureRBrace(cur); cur = getPrev(cur)) {
        if (!StringUtil.contains(text, cur.getTextRange().getEndOffset(), first.getTextRange().getStartOffset(), '\n')) return true;
        first = cur;
      }

      return false;
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
    LOG.assertTrue(GeeseUtil.isClosureRBrace(element));

    PsiFile file = element.getContainingFile();
    Document document = file.getViewProvider().getDocument();

    PsiElement first = null;
    PsiElement last = null;
    for (PsiElement cur = element; GeeseUtil.isClosureRBrace(cur); cur = getNext(cur)) {
      last = cur;
    }

    for (PsiElement cur = element; GeeseUtil.isClosureRBrace(cur); cur = getPrev(cur)) {
      first = cur;
    }

    LOG.assertTrue(first != null);
    LOG.assertTrue(last != null);


    RangeMarker rangeMarker = document.createRangeMarker(first.getTextRange().getStartOffset(), last.getTextRange().getEndOffset());

    String text = document.getText();
    for (PsiElement cur = getPrev(last); GeeseUtil.isClosureRBrace(cur); cur = getPrev(cur)) {
      int offset = last.getTextRange().getStartOffset();
      if (!StringUtil.contains(text, cur.getTextRange().getEndOffset(), offset, '\n')) {
        document.insertString(offset, "\n");
      }
      last = cur;
    }


    CodeStyleManager.getInstance(context.project()).reformatText(file, rangeMarker.getStartOffset(), rangeMarker.getEndOffset());
  }


  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return MY_PREDICATE;
  }
}
