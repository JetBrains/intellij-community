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
package org.jetbrains.plugins.groovy.refactoring.introduce;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;

import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class StringPartInfo {
  private final GrLiteral myLiteral;
  private final TextRange myRange;
  private final List<GrStringInjection> myInjections;

  @Nullable
  public static StringPartInfo findStringPart(@NotNull PsiFile file, int startOffset, int endOffset) {
    final PsiElement start = file.findElementAt(startOffset);
    final PsiElement fin = file.findElementAt(endOffset - 1);
    if (start == null || fin == null) return null;

    final PsiElement psi = PsiTreeUtil.findCommonParent(start, fin);
    if (psi == null) return null;

    GrLiteral literal = findLiteral(psi);
    if (literal != null && !literal.getTextRange().equalsToRange(startOffset, endOffset)) {
      return new StringPartInfo(literal, new TextRange(startOffset, endOffset));
    }

    return null;
  }

  @Nullable
  private static GrLiteral findLiteral(@NotNull PsiElement psi) {
    if (isStringLiteral(psi.getParent())) {
      return (GrLiteral)psi.getParent();
    }

    if (isStringLiteral(psi.getParent().getParent())) {
      return (GrLiteral)psi.getParent().getParent();
    }

    if (psi instanceof GrString) {
      return (GrLiteral)psi;
    }

    return null;
  }

  private static boolean isStringLiteral(final PsiElement psi) {
    return psi instanceof GrLiteral && TokenSets.STRING_LITERAL_SET.contains(GrLiteralImpl.getLiteralType((GrLiteral)psi));
  }

  public StringPartInfo(@NotNull GrLiteral literal, @NotNull final TextRange range) {
    myLiteral = literal;
    myRange = range.shiftRight(-literal.getTextRange().getStartOffset());

    if (literal instanceof GrString) {
      final GrStringInjection[] injections = ((GrString)literal).getInjections();
      myInjections = ContainerUtil.filter(injections, new Condition<GrStringInjection>() {
        @Override
        public boolean value(GrStringInjection injection) {
          return range.contains(injection.getTextRange());
        }
      });
    }
    else {
      myInjections = Collections.emptyList();
    }
  }

  @NotNull
  public GrLiteral getLiteral() {
    return myLiteral;
  }

  @NotNull
  public TextRange getRange() {
    return myRange;
  }

  @NotNull
  public List<GrStringInjection> getInjections() {
    return myInjections;
  }
}
