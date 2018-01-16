/*
 * Copyright 2007-2018 Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import javax.swing.*;

public class ToArrayCallWithZeroLengthArrayArgumentInspection extends BaseInspection {
  private static final CallMatcher COLLECTION_SIZE =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "size").parameterCount(0);
  private static final CallMatcher COLLECTION_TO_ARRAY =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "toArray").parameterCount(1);
  private static final String PREFER_EMPTY_ARRAY_SETTING = "PreferEmptyArray";

  private static final PreferEmptyArray DEFAULT_MODE = PreferEmptyArray.ALWAYS;

  public enum PreferEmptyArray {
    ALWAYS("Always"), BY_LEVEL("According to language level"), NEVER("Never (prefer pre-sized array)");

    private final String myMessage;

    PreferEmptyArray(String message) { myMessage = message; }

    String getMessage() { return myMessage; }

    boolean isEmptyPreferred(PsiExpression expression) {
      switch (this) {
        case ALWAYS:
          return true;
        case NEVER:
          return false;
        default:
          return PsiUtil.isLanguageLevel7OrHigher(expression);
      }
    }

    @NotNull
    static PreferEmptyArray from(String name) {
      return StreamEx.of(values()).filterBy(PreferEmptyArray::name, name).findFirst().orElse(DEFAULT_MODE);
    }
  }

  @NotNull
  public PreferEmptyArray myMode = DEFAULT_MODE;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 5, true, false));
    panel.add(new JLabel("Prefer empty array:"));

    ButtonGroup group = new ButtonGroup();
    for (PreferEmptyArray mode : PreferEmptyArray.values()) {
      JRadioButton radioButton = new JRadioButton(mode.getMessage(), mode == myMode);
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
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("to.array.call.style.display.name");
  }

  @Override
  public void readSettings(@NotNull Element node) {
    Element element = node.getChild(PREFER_EMPTY_ARRAY_SETTING);
    if (element != null) {
      myMode = PreferEmptyArray.from(element.getAttributeValue("value"));
    }
  }

  @Override
  public void writeSettings(@NotNull Element node) {
    if (myMode != DEFAULT_MODE) {
      Element element = new Element(PREFER_EMPTY_ARRAY_SETTING);
      element.setAttribute("value", myMode.toString());
      node.addContent(element);
    }
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
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
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
    PsiMethodCallExpression maybeSizeCall =
      ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(dimensions[0]), PsiMethodCallExpression.class);
    if (COLLECTION_SIZE.test(maybeSizeCall)) {
      PsiExpression sizeQualifier = maybeSizeCall.getMethodExpression().getQualifierExpression();
      return sizeQualifier != null && PsiEquivalenceUtil.areElementsEquivalent(sizeQualifier, qualifier);
    }
    return false;
  }

  private static class ToArrayCallWithZeroLengthArrayArgumentFix extends InspectionGadgetsFix {
    private boolean myEmptyPreferred;

    public ToArrayCallWithZeroLengthArrayArgumentFix(boolean emptyPreferred) {
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
    protected void doFix(Project project, ProblemDescriptor descriptor) {
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


      if (myEmptyPreferred || ExpressionUtils.isSimpleExpression(qualifier)) {
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