// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrSpreadArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTupleAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;

import java.util.ArrayList;
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
      checkTupleAssignment(initializer, types);
    }
  }

  @Override
  public void visitVariableDeclaration(@NotNull GrVariableDeclaration variableDeclaration) {
    if (variableDeclaration.isTuple()) {
      GrExpression initializer = variableDeclaration.getTupleInitializer();
      if (initializer != null ) {
        PsiType[] types = Arrays.stream(variableDeclaration.getVariables()).map(it -> it.getType()).toArray(PsiType[]::new);
        checkTupleAssignment(initializer, types);
      }
    }
    super.visitVariableDeclaration(variableDeclaration);
  }

  void checkTupleAssignment(@NotNull GrExpression initializer, final PsiType @NotNull [] types) {
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
      return;
    }

    registerError(
      initializer,
      GroovyBundle.message("multiple.assignments.without.list.expr"),
      new LocalQuickFix[]{GroovyQuickFixFactory.getInstance().createMultipleAssignmentFix(types.length)},
      ProblemHighlightType.GENERIC_ERROR
    );
  }

  @Override
  protected void registerError(@NotNull final PsiElement location,
                               @NotNull final String description,
                               final LocalQuickFix @Nullable [] fixes,
                               final ProblemHighlightType highlightType) {
    if (highlightType != ProblemHighlightType.GENERIC_ERROR) return;
    final List<IntentionAction> intentions = new ArrayList<>();
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
    registerError(location, description, intentions.toArray(IntentionAction.EMPTY_ARRAY), highlightType);
  }

  protected void registerError(@NotNull final PsiElement location,
                               @NotNull final String description,
                               final IntentionAction @Nullable [] fixes,
                               final ProblemHighlightType highlightType) {
    if (highlightType != ProblemHighlightType.GENERIC_ERROR) return;
    AnnotationBuilder builder = myHolder.newAnnotation(HighlightSeverity.ERROR, description).range(location);
    if (fixes != null) {
      for (IntentionAction intention : fixes) {
        builder = builder.withFix(intention);
      }
    }
    builder.create();
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

  @Override
  public void visitSpreadArgument(@NotNull GrSpreadArgument spreadArgument) {
    registerError(
      spreadArgument,
      GroovyBundle.message("spread.operator.is.not.available"),
      buildSpreadArgumentFix(spreadArgument),
      ProblemHighlightType.GENERIC_ERROR
    );
  }

  private static LocalQuickFix[] buildSpreadArgumentFix(GrSpreadArgument spreadArgument) {
    GrCallExpression parent = PsiTreeUtil.getParentOfType(spreadArgument, GrCallExpression.class);
    if (parent == null) return LocalQuickFix.EMPTY_ARRAY;
    PsiMethod resolveMethod = parent.resolveMethod();

    if (resolveMethod == null) return LocalQuickFix.EMPTY_ARRAY;

    return new LocalQuickFix[]{ GroovyQuickFixFactory.getInstance().createSpreadArgumentFix(resolveMethod.getParameters().length)};
  }
}
