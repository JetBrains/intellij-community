/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.spellchecker;

import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.spellchecker.inspections.SplitterFactory;
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


  private final String[] excludedTags = new String[]{"author", "link","see","by"};

  @Nullable
  @Override
  public Token[] tokenize(@NotNull PsiDocComment comment) {
    List<Token> result = new ArrayList<Token>();
    for (PsiElement el : comment.getChildren()) {
      if (el instanceof PsiDocTag) {
        PsiDocTag tag = (PsiDocTag)el;
        if (!Arrays.asList(excludedTags).contains(tag.getName())) {
          for (PsiElement data : tag.getDataElements()) {
            result.add(new Token<PsiElement>(data, data.getText(),false, SplitterFactory.getInstance().getCommentSplitter()));
          }
        }
      }
      else {
        result.add(new Token<PsiElement>(el, el.getText(),false, SplitterFactory.getInstance().getCommentSplitter()));
      }
    }
    Token[] t = new Token[result.size()];
    result.toArray(t);
    return result.size() == 0 ? null : t;
  }
}
