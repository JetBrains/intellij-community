/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.introduce.constant;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrInplaceIntroducer;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContextImpl;
import org.jetbrains.plugins.groovy.refactoring.introduce.field.GrFieldNameSuggester;
import org.jetbrains.plugins.groovy.refactoring.introduce.field.GroovyInplaceFieldValidator;

import javax.swing.*;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by Max Medvedev on 8/29/13
 */
public class GrInplaceConstantIntroducer extends GrInplaceIntroducer {
  private final GrInplaceIntroduceConstantPanel myPanel;
  private final GrIntroduceContext myContext;
  private final RangeMarker myExpressionRangeMarker;
  private final RangeMarker myStringPartRangeMarker;
  private final boolean myReplaceAll;

  public GrInplaceConstantIntroducer(GrVariable var,
                                     GrIntroduceContext context,
                                     List<RangeMarker> occurrences,
                                     boolean replaceAllOccurrences,
                                     RangeMarker expressionRangeMarker,
                                     RangeMarker stringPartRangeMarker) {
    super(var, context.getEditor(), context.getProject(), GrIntroduceConstantHandler.REFACTORING_NAME, occurrences, context.getPlace());

    myContext = context;
    myReplaceAll = replaceAllOccurrences;
    myExpressionRangeMarker = expressionRangeMarker;
    myStringPartRangeMarker = stringPartRangeMarker;

    myPanel = new GrInplaceIntroduceConstantPanel();
  }

  @Override
  public LinkedHashSet<String> suggestNames(GrIntroduceContext context) {
    return new GrFieldNameSuggester(context , new GroovyInplaceFieldValidator(context), false).suggestNames();
  }

  @Override
  protected void moveOffsetAfter(boolean success) {
    if (success) {
      final GrVariable field = getVariable();
      assert field != null;
      GrIntroduceConstantProcessor processor = new GrIntroduceConstantProcessor(generateContext(), generateSettings()) {
        @Override
        protected GrVariableDeclaration addDeclaration(PsiClass targetClass, GrVariableDeclaration declaration) {
          return (GrVariableDeclaration)field.getParent();
        }

        @Override
        protected boolean checkErrors(@NotNull PsiClass targetClass) {
          return false;
        }
      };
      processor.run();
    }
    super.moveOffsetAfter(success);
  }

  private GrIntroduceContext generateContext() {
    final List<RangeMarker> occurrenceMarkers = getOccurrenceMarkers();

    List<PsiElement> occurrences = ContainerUtil.newArrayList();
    for (RangeMarker marker : occurrenceMarkers) {
      ContainerUtil.addIfNotNull(occurrences, findExpression(marker));
    }

    GrExpression expr = null;
    if (myExpressionRangeMarker != null) expr = findExpression(myExpressionRangeMarker);
    if (myStringPartRangeMarker != null) {
      expr = findExpressionFromStringPartMarker(myStringPartRangeMarker);
      occurrences.add(expr);
    }

    return new GrIntroduceContextImpl(myContext.getProject(), myContext.getEditor(), expr, null, null, PsiUtilCore.toPsiElementArray(
      occurrences), myContext.getScope());
  }

  @Nullable
  private GrExpression findExpressionFromStringPartMarker(RangeMarker marker) {
    PsiFile file = PsiDocumentManager.getInstance(myContext.getProject()).getPsiFile(marker.getDocument());
    if (file == null) return null;
    PsiElement leaf = file.findElementAt(marker.getStartOffset());
    GrBinaryExpression binary = PsiTreeUtil.getParentOfType(leaf, GrBinaryExpression.class);
    if (binary != null) {
      return binary.getRightOperand();
    }
    return null;
  }

  @Nullable
  private GrExpression findExpression(@NotNull RangeMarker marker) {
    PsiFile file = PsiDocumentManager.getInstance(myContext.getProject()).getPsiFile(marker.getDocument());
    if (file == null) return null;
    PsiElement leaf = file.findElementAt(marker.getStartOffset());
    if (leaf != null && leaf.getParent() instanceof GrReferenceExpression) {
      return (GrExpression)leaf.getParent();
    }
    return null;
  }

  private GrIntroduceConstantSettings generateSettings() {
    return new GrIntroduceConstantSettings() {
      @Override
      public String getVisibilityModifier() {
        return PsiModifier.PUBLIC;
      }

      @Nullable
      @Override
      public String getName() {
        return getVariable().getName();
      }

      @Override
      public boolean replaceAllOccurrences() {
        return myReplaceAll;
      }

      @Nullable
      @Override
      public PsiType getSelectedType() {
        return getVariable().getDeclaredType();
      }

      @Nullable
      @Override
      public PsiClass getTargetClass() {
        return (PsiClass)myContext.getScope();
      }
    };
  }

  @Nullable
  @Override
  protected JComponent getComponent() {
    //return myPanel.getRootPane();
    return null;
  }

  @Nullable
  @Override
  protected PsiElement checkLocalScope() {
    return getVariable().getContainingFile();
  }

}
