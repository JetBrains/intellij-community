/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;

/**
* Created by Max Medvedev on 14/05/14
*/
class GrStatementStartCompletionProvider extends CompletionProvider<CompletionParameters> {
  private static final PsiElementPattern.Capture<PsiElement> STATEMENT_START =
    PlatformPatterns.psiElement(GroovyTokenTypes.mIDENT).andOr(
      PlatformPatterns.psiElement().afterLeaf(StandardPatterns.or(
        PlatformPatterns.psiElement().isNull(),
        PlatformPatterns.psiElement().withElementType(TokenSets.SEPARATORS),
        PlatformPatterns.psiElement(GroovyTokenTypes.mLCURLY),
        PlatformPatterns.psiElement(GroovyTokenTypes.kELSE)
      )).andNot(PlatformPatterns.psiElement().withParent(GrTypeDefinitionBody.class))
        .andNot(PlatformPatterns.psiElement(PsiErrorElement.class)),
      PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(GroovyTokenTypes.mRPAREN)).withSuperParent(2, StandardPatterns.or(
        PlatformPatterns.psiElement(GrForStatement.class),
        PlatformPatterns.psiElement(GrWhileStatement.class),
        PlatformPatterns.psiElement(GrIfStatement.class)
      ))
    );

  public static void register(CompletionContributor contributor) {
    contributor.extend(CompletionType.BASIC, STATEMENT_START, new GrStatementStartCompletionProvider());
  }

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    result.addElement(LookupElementBuilder.create("if").bold().withInsertHandler(new InsertHandler<LookupElement>() {
      @Override
      public void handleInsert(InsertionContext context, LookupElement item) {
        if (context.getCompletionChar() != ' ') {
          TailTypes.IF_LPARENTH.processTail(context.getEditor(), context.getTailOffset());
        }
        if (context.getCompletionChar() == '(') {
          context.setAddCompletionChar(false);
        }
      }
    }));
  }
}
