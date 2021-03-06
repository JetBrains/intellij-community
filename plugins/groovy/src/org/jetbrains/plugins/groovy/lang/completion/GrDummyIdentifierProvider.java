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

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;

public class GrDummyIdentifierProvider {
  public static final String DUMMY_IDENTIFIER_DECAPITALIZED = StringUtil.decapitalize(CompletionUtil.DUMMY_IDENTIFIER);

  private final CompletionInitializationContext myContext;

  public GrDummyIdentifierProvider(@NotNull CompletionInitializationContext context) {
    myContext = context;
  }

  @Nullable
  public String getIdentifier() {
    if (myContext.getCompletionType() == CompletionType.BASIC && myContext.getFile() instanceof GroovyFile) {
      PsiElement position = myContext.getFile().findElementAt(myContext.getStartOffset());
      if (position != null &&
          position.getParent() instanceof GrVariable &&
          position == ((GrVariable)position.getParent()).getNameIdentifierGroovy() ||

          position != null &&
          position.getParent() instanceof GrAnnotationNameValuePair &&
          position == ((GrAnnotationNameValuePair)position.getParent()).getNameIdentifierGroovy()) {

        return CompletionUtil.DUMMY_IDENTIFIER_TRIMMED;
      }
      else if (isIdentifierBeforeLParenth(position)) {
        return setCorrectCase() + ";";
      }
      else if (GroovyCompletionUtil.isInPossibleClosureParameter(position)) {
        return setCorrectCase() + "->";
      }
      else if (isIdentifierBeforeAssign(position)) {
        return CompletionUtil.DUMMY_IDENTIFIER_TRIMMED;
      }
      else {
        return setCorrectCase();
      }
    }
    return null;
  }

  @NotNull
  private String setCorrectCase() {
    final PsiElement element = myContext.getFile().findElementAt(myContext.getStartOffset());
    if (element == null) return DUMMY_IDENTIFIER_DECAPITALIZED;

    final String text = element.getText();
    if (text.isEmpty()) return DUMMY_IDENTIFIER_DECAPITALIZED;

    return Character.isUpperCase(text.charAt(0)) ? CompletionInitializationContext.DUMMY_IDENTIFIER : DUMMY_IDENTIFIER_DECAPITALIZED;
  }

  private static boolean isIdentifierBeforeAssign(@Nullable PsiElement position) {
    ASTNode node = findNodeAfterIdent(position);
    return node != null && node.getElementType() == GroovyTokenTypes.mASSIGN;
  }

  private static boolean isIdentifierBeforeLParenth(@Nullable PsiElement position) {
    //<caret>String name=
    ASTNode node = findNodeAfterIdent(position);
    return node != null && node.getElementType() == GroovyTokenTypes.mLPAREN;
  }

  @Nullable
  private static ASTNode findNodeAfterIdent(@Nullable PsiElement position) {
    if (position == null) {
      return null;
    }
    ASTNode node = position.getNode();
    if (node.getElementType() == GroovyTokenTypes.mIDENT) {
      node = TreeUtil.nextLeaf(node);
    }
    node = TreeUtil.skipWhitespaceAndComments(node, true);
    return node;
  }
}
