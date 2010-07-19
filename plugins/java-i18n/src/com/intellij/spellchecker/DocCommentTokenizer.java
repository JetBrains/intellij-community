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
import com.intellij.spellchecker.inspections.CommentSplitter;
import com.intellij.spellchecker.inspections.SplitterFactory;
import com.intellij.spellchecker.tokenizer.Token;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class DocCommentTokenizer extends Tokenizer<PsiDocComment> {

  private static final Set<String> excludedTags = new HashSet<String>();
  {
    excludedTags.add("author");
    excludedTags.add("see");
    excludedTags.add("by");
    excludedTags.add("link");
  }

  @Nullable
  @Override
  public Token[] tokenize(@NotNull PsiDocComment comment) {
    List<Token> result = new ArrayList<Token>();
    final CommentSplitter splitter = SplitterFactory.getInstance().getCommentSplitter();

    for (PsiElement el : comment.getChildren()) {
      if (el instanceof PsiDocTag) {
        PsiDocTag tag = (PsiDocTag)el;
        if (!excludedTags.contains(tag.getName())) {
          for (PsiElement data : tag.getDataElements()) {
            result.add(new Token<PsiElement>(data, splitter));
          }
        }
      }
      else {
        result.add(new Token<PsiElement>(el, splitter));
      }
    }
    Token[] t = new Token[result.size()];
    result.toArray(t);
    return result.size() == 0 ? null : t;
  }
}
