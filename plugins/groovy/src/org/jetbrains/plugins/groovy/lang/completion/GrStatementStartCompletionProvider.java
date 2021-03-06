// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
                                @NotNull ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    result.addElement(LookupElementBuilder.create("if").bold().withInsertHandler(new InsertHandler<>() {
      @Override
      public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
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
