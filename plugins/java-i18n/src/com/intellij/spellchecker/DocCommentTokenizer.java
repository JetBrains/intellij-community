package com.intellij.spellchecker;

import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.spellchecker.tokenizer.Token;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class DocCommentTokenizer extends Tokenizer<PsiDocComment> {


  private final String[] excludedTags = new String[]{"author", "link"};

  @Nullable
  @Override
  public Token[] tokenize(@NotNull PsiDocComment comment) {
    List<Token> result = new ArrayList<Token>();
    for (PsiElement el : comment.getChildren()) {
      if (el instanceof PsiDocTag) {
        PsiDocTag tag = (PsiDocTag)el;
        if (!Arrays.asList(excludedTags).contains(tag.getName())) {
          for (PsiElement data : tag.getDataElements()) {
            result.add(new Token<PsiElement>(data, data.getText(),false));
          }
        }
      }
      else {
        result.add(new Token<PsiElement>(el, el.getText(),false));
      }
    }
    Token[] t = new Token[result.size()];
    result.toArray(t);
    return result.size() == 0 ? null : t;
  }
}
