// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.*;

import javax.swing.*;

public class ToArrayCallWithZeroLengthArrayArgumentInspection extends BaseInspection {
  private static final CallMatcher COLLECTION_TO_ARRAY =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "toArray").parameterCount(1);

  private static final PreferEmptyArray DEFAULT_MODE = PreferEmptyArray.ALWAYS;

  public enum PreferEmptyArray {
    ALWAYS {
      @Override @Nls String getMessage() { return InspectionGadgetsBundle.message("prefer.empty.array.options.mode.always"); }
    },
    BY_LEVEL {
      @Override @Nls String getMessage() { return InspectionGadgetsBundle.message("prefer.empty.array.options.mode.by.level"); }
    },
    NEVER {
      @Override @Nls String getMessage() { return InspectionGadgetsBundle.message("prefer.empty.array.options.mode.always.never"); }
    };

    abstract @Nls String getMessage();

    boolean isEmptyPreferred(PsiExpression expression) {
      return switch (this) {
        case ALWAYS -> true;
        case NEVER -> false;
        default -> PsiUtil.isLanguageLevel7OrHigher(expression);
      };
    }
  }

  @NotNull
  @SuppressWarnings("PublicField")
  public PreferEmptyArray myMode = DEFAULT_MODE;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 5, true, false));
    panel.add(new JLabel(InspectionGadgetsBundle.message("prefer.empty.array.options.title")));

    ButtonGroup group = new ButtonGroup();
    for (PreferEmptyArray mode : PreferEmptyArray.values()) {
      JRadioButton radioButton = new JRadioButton(mode.getMessage(), mode == myMode);
      radioButton.setBorder(JBUI.Borders.emptyLeft(20));
      radioButton.addActionListener(e -> myMode = mode);
      panel.add(radioButton);
      group.add(radioButton);
    }

    return panel;
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiExpression argument = (PsiExpression)infos[1];
    return new ToArrayCallWithZeroLengthArrayArgumentFix(myMode.isEmptyPreferred(argument));
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiExpression argument = (PsiExpression)infos[1];
    return myMode.isEmptyPreferred(argument) ?
           InspectionGadgetsBundle.message("to.array.call.style.problem.descriptor.presized", argument.getText()) :
           InspectionGadgetsBundle.message("to.array.call.style.problem.descriptor.zero", argument.getText());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        if (!COLLECTION_TO_ARRAY.test(call)) return;
        final PsiExpression argument = call.getArgumentList().getExpressions()[0];
        final PsiType type = argument.getType();
        if (!(type instanceof PsiArrayType)) return;
        if (type.getArrayDimensions() != 1) return;

        boolean wrongArray =
          myMode.isEmptyPreferred(argument)
          ? isPresizedArray(argument, call.getMethodExpression().getQualifierExpression())
          : isEmptyArray(argument);
        if (wrongArray) {
          registerMethodCallError(call, call, argument);
        }
      }
    };
  }

  private static boolean isEmptyArray(@Nullable PsiExpression argument) {
    if (argument instanceof PsiReferenceExpression) {
      final PsiElement element = ((PsiReferenceExpression)argument).resolve();
      if (!(element instanceof PsiField)) return false;
      return CollectionUtils.isConstantEmptyArray((PsiField)element);
    }
    return ConstructionUtils.isEmptyArrayInitializer(argument);
  }

  @Contract("_, null -> false")
  private static boolean isPresizedArray(@Nullable PsiExpression argument, @Nullable PsiExpression qualifier) {
    if (qualifier == null) return false;
    PsiNewExpression newExpression = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(argument), PsiNewExpression.class);
    if (newExpression == null) return false;
    PsiExpression[] dimensions = newExpression.getArrayDimensions();
    if (dimensions.length != 1) return false;
    return CollectionUtils.isCollectionOrMapSize(dimensions[0], qualifier);
  }

  private static class ToArrayCallWithZeroLengthArrayArgumentFix extends InspectionGadgetsFix {
    private final boolean myEmptyPreferred;

    ToArrayCallWithZeroLengthArrayArgumentFix(boolean emptyPreferred) {
      myEmptyPreferred = emptyPreferred;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return myEmptyPreferred ?
             InspectionGadgetsBundle.message("to.array.call.style.quickfix.make.zero") :
             InspectionGadgetsBundle.message("to.array.call.style.quickfix.make.presized");
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("to.array.call.style.quickfix.family.name");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) return;
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) return;
      final PsiExpression argument = arguments[0];
      if (qualifier == null) return;

      final String collectionText = qualifier.getText();
      final PsiType type = argument.getType();
      if (type == null) return;
      final PsiType componentType = type.getDeepComponentType();
      final String typeText = componentType.getCanonicalText();


      if (myEmptyPreferred || ExpressionUtils.isSafelyRecomputableExpression(qualifier)) {
        CommentTracker ct = new CommentTracker();
        String sizeClause = myEmptyPreferred ? "0" : collectionText + ".size()";
        @NonNls final String replacementText = "new " + typeText + '[' + sizeClause + "]";
        ct.replaceAndRestoreComments(argument, replacementText);
        return;
      }
      // need to introduce a variable to prevent calling a method twice
      PsiStatement statement = PsiTreeUtil.getParentOfType(methodCallExpression, PsiStatement.class);
      if (statement == null) return;
      final PsiType qualifierType = qualifier.getType();
      if (qualifierType == null) return;
      PsiDeclarationStatement declarationStatement = factory.createVariableDeclarationStatement("var", qualifierType, qualifier);
      PsiElement statementParent = statement.getParent();
      while (statementParent instanceof PsiLoopStatement || statementParent instanceof PsiIfStatement) {
        statement = (PsiStatement)statementParent;
        statementParent = statement.getParent();
      }
      final String toArrayText = "var.toArray(new " + typeText + "[var.size()])";
      PsiMethodCallExpression newMethodCallExpression =
        (PsiMethodCallExpression)factory.createExpressionFromText(toArrayText, methodCallExpression);
      declarationStatement = (PsiDeclarationStatement)statementParent.addBefore(declarationStatement, statement);
      newMethodCallExpression = (PsiMethodCallExpression)methodCallExpression.replace(newMethodCallExpression);
      showRenameTemplate(declarationStatement, newMethodCallExpression, statementParent);
    }

    private void showRenameTemplate(PsiDeclarationStatement declarationStatement, PsiMethodCallExpression methodCallExpression,
                                    PsiElement context) {
      if (!isOnTheFly()) {
        return;
      }
      final PsiVariable variable = (PsiVariable)declarationStatement.getDeclaredElements()[0];
      final PsiReferenceExpression ref1 = (PsiReferenceExpression)methodCallExpression.getMethodExpression().getQualifierExpression();
      final PsiNewExpression argument = (PsiNewExpression)methodCallExpression.getArgumentList().getExpressions()[0];
      final PsiMethodCallExpression sizeExpression = (PsiMethodCallExpression)argument.getArrayDimensions()[0];
      final PsiReferenceExpression ref2 = (PsiReferenceExpression)sizeExpression.getMethodExpression().getQualifierExpression();
      HighlightUtils.showRenameTemplate(context, variable, ref1, ref2);
    }
  }
}