/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.type;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GrReplaceMultiAssignmentFix;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTupleAssignmentExpression;

import java.util.Arrays;
import java.util.List;

public class GroovyStaticTypeCheckVisitor extends GroovyTypeCheckVisitor {
  private AnnotationHolder myHolder;

  @Override
  public void visitTupleAssignmentExpression(@NotNull GrTupleAssignmentExpression expression) {
    super.visitTupleAssignmentExpression(expression);
    final GrExpression initializer = expression.getRValue();
    if (initializer!= null) {
      PsiType[] types = Arrays.stream(expression.getLValue().getExpressions()).map(it -> it.getType()).toArray(PsiType[]::new);
      checkTupleAssignment(initializer, types, expression);
    }
  }

  @Override
  public void visitVariableDeclaration(@NotNull GrVariableDeclaration variableDeclaration) {
    if (variableDeclaration.isTuple()) {
      GrExpression initializer = variableDeclaration.getTupleInitializer();
      if (initializer != null ) {
        PsiType[] types = Arrays.stream(variableDeclaration.getVariables()).map(it -> it.getType()).toArray(PsiType[]::new);
        checkTupleAssignment(initializer, types, variableDeclaration);
      }
    }
    super.visitVariableDeclaration(variableDeclaration);
  }

  void checkTupleAssignment(@NotNull GrExpression initializer, @NotNull final PsiType[] types, @NotNull PsiElement context) {
    if (initializer instanceof GrListOrMap && !((GrListOrMap)initializer).isMap()) {

      final GrListOrMap initializerList = (GrListOrMap)initializer;

      final GrExpression[] expressions = initializerList.getInitializers();
      if (types.length > expressions.length) {
        registerError(
          initializer,
          GroovyBundle.message("incorrect.number.of.values", types.length, expressions.length),
          LocalQuickFix.EMPTY_ARRAY,
          ProblemHighlightType.GENERIC_ERROR
        );
      }
      else {
        for (int i = 0; i < types.length; i++) {
          processAssignmentWithinMultipleAssignment(types[i], expressions[i].getType(), context, expressions[i]);
        }
      }
      return;
    }

    registerError(
      initializer,
      GroovyBundle.message("multiple.assignments.without.list.expr"),
      new LocalQuickFix[]{new GrReplaceMultiAssignmentFix(types.length)},
      ProblemHighlightType.GENERIC_ERROR
    );
  }

  @Override
  protected void registerError(@NotNull final PsiElement location,
                               @NotNull final String description,
                               @Nullable final LocalQuickFix[] fixes,
                               final ProblemHighlightType highlightType) {
    if (highlightType != ProblemHighlightType.GENERIC_ERROR) return;
    final List<IntentionAction> intentions = ContainerUtil.newArrayList();
    if (fixes != null) {
      for (final LocalQuickFix fix : fixes) {
        intentions.add(new IntentionAction() {
          @NotNull
          @Override
          public String getText() {
            return fix.getName();
          }

          @NotNull
          @Override
          public String getFamilyName() {
            return fix.getFamilyName();
          }

          @Override
          public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
            return true;
          }

          @Override
          public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
            final InspectionManager manager = InspectionManager.getInstance(project);
            final ProblemDescriptor descriptor =
              manager.createProblemDescriptor(location, description, fixes, highlightType, fixes.length == 1, false);
            fix.applyFix(project, descriptor);
          }

          @Override
          public boolean startInWriteAction() {
            return true;
          }
        });
      }
    }
    registerError(location, description, intentions.toArray(new IntentionAction[intentions.size()]), highlightType);
  }

  protected void registerError(@NotNull final PsiElement location,
                               @NotNull final String description,
                               @Nullable final IntentionAction[] fixes,
                               final ProblemHighlightType highlightType) {
    if (highlightType != ProblemHighlightType.GENERIC_ERROR) return;
    final Annotation annotation = myHolder.createErrorAnnotation(location, description);
    if (fixes == null) return;
    for (IntentionAction intention : fixes) {
      annotation.registerFix(intention);
    }
  }

  @Override
  public void visitElement(@NotNull GroovyPsiElement element) {
    // do nothing & disable recursion
  }

  public void accept(@NotNull GroovyPsiElement element, @NotNull AnnotationHolder holder) {
    myHolder = holder;
    try {
      element.accept(this);
    }
    finally {
      myHolder = null;
    }
  }
}
