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
package org.jetbrains.plugins.groovy.refactoring.introduce.parameter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.introduceParameter.IntroduceParameterData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author Maxim.Medvedev
 */
public class GrExpressionWrapper implements IntroduceParameterData.ExpressionWrapper {
  private final GrExpression myExpression;
  private final RangeMarker myMarker;
  private final PsiFile myFile;

  public GrExpressionWrapper(@NotNull GrExpression expression) {
    assert expression.isValid();

    myExpression = expression;
    myFile = expression.getContainingFile();
    if (myFile.isPhysical()) {
      Document document = PsiDocumentManager.getInstance(expression.getProject()).getDocument(myFile);
      assert document != null;
      myMarker = document.createRangeMarker(myExpression.getTextRange());
    }
    else {
      myMarker = null;
    }
  }

  @NotNull
  @Override
  public String getText() {
    return myExpression.getText();
  }

  @Override
  public PsiType getType() {
    return myExpression.getType();
  }

  @NotNull
  @Override
  public GrExpression getExpression() {
    if (myExpression.isValid()) {
      return myExpression;
    }
    else if (myMarker != null && myMarker.isValid()) {
      PsiElement at = myFile.findElementAt(myMarker.getStartOffset());
      if (at != null) {
        return GroovyPsiElementFactory.getInstance(myFile.getProject()).createExpressionFromText(myExpression.getText(), at);
      }
    }

    return GroovyPsiElementFactory.getInstance(myFile.getProject()).createExpressionFromText(myExpression.getText(), null);
  }
}
