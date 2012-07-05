/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.spellchecker;

import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.spellchecker.inspections.CommentSplitter;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Max Medvedev
 */
public class GrDocCommentTokenizer extends Tokenizer<PsiDocComment> {
  private static final Set<String> excludedTags = ContainerUtil.immutableSet("author", "see", "by", "link");

  @Override
  public void tokenize(@NotNull PsiDocComment comment, TokenConsumer consumer) {
    final CommentSplitter splitter = CommentSplitter.getInstance();

    for (PsiElement el : comment.getChildren()) {
      if (el instanceof PsiDocTag) {
        PsiDocTag tag = (PsiDocTag)el;
        if (!excludedTags.contains(tag.getName())) {
          for (PsiElement data : tag.getDataElements()) {
            consumer.consumeToken(data, splitter);
          }
        }
      }
      else {
        consumer.consumeToken(el, splitter);
      }
    }
  }
}
