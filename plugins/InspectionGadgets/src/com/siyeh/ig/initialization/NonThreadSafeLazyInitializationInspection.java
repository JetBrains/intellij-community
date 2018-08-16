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
package com.siyeh.ig.initialization;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * @author Bas Leijdekkers
 */
public class NonThreadSafeLazyInitializationInspection extends BaseInspection {

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiIfStatement ifStatement = (PsiIfStatement)infos[0];
    final PsiField field = (PsiField)infos[1];
    if (!isStaticAndAssignedOnce(field) || !isSafeToDeleteIfStatement(ifStatement, field)) {
      return null;
    }
    return new IntroduceHolderFix();
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "non.thread.safe.lazy.initialization.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "non.thread.safe.lazy.initialization.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnsafeSafeLazyInitializationVisitor();
  }

  private static boolean isStaticAndAssignedOnce(PsiField field) {
    if (!field.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    final int[] writeCount = new int[1];
    return ReferencesSearch.search(field).forEach(reference -> {
      final PsiElement element = reference.getElement();
      if (!(element instanceof PsiExpression) || !PsiUtil.isAccessedForWriting((PsiExpression)element)) {
        return true;
      }
      return ++writeCount[0] != 2;
    });
  }

  private static boolean isSafeToDeleteIfStatement(PsiIfStatement ifStatement, PsiField field) {
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    final PsiStatement thenBranch = ifStatement.getThenBranch();
    if (thenBranch == null) {
      return false;
    }
    final PsiStatement statement = ControlFlowUtils.stripBraces(thenBranch);
    if (!(statement instanceof PsiExpressionStatement)) {
      return false;
    }
    final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
    return isSimpleAssignment(expressionStatement, field);
  }

  private static boolean isSimpleAssignment(PsiExpressionStatement expressionStatement, PsiField field) {
    final PsiExpression expression = expressionStatement.getExpression();
    if (!(expression instanceof PsiAssignmentExpression)) {
      return false;
    }
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
    final PsiExpression lhs = ParenthesesUtils.stripParentheses(assignmentExpression.getLExpression());
    if (!(lhs instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
    final PsiElement target = referenceExpression.resolve();
    if (!field.equals(target)) {
      return false;
    }
    final Collection<PsiReferenceExpression> referenceChildren =
      PsiTreeUtil.findChildrenOfType(assignmentExpression.getRExpression(), PsiReferenceExpression.class);
    for (PsiReferenceExpression child : referenceChildren) {
      final PsiElement target2 = child.resolve();
      if (!(target2 instanceof PsiMember)) {
        return false;
      }
      final PsiMember member = (PsiMember)target2;
      if (!member.hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }
    }
    return true;
  }

  private static class IntroduceHolderFix extends InspectionGadgetsFix {

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiReferenceExpression expression = (PsiReferenceExpression)descriptor.getPsiElement();
      final PsiElement resolved = expression.resolve();
      if (!(resolved instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)resolved;
      @NonNls final String holderName = StringUtil.capitalize(field.getName()) + "Holder";
      final PsiElement expressionParent = expression.getParent();
      if (!(expressionParent instanceof PsiAssignmentExpression)) {
        return;
      }
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expressionParent;
      final PsiExpression rhs = assignmentExpression.getRExpression();
      if (rhs == null) {
        return;
      }
      @NonNls final String text = "private static class " + holderName + " {" +
                                  "private static final " + field.getType().getCanonicalText() + " " +
                                  field.getName() + " = " + rhs.getText() + ";}";
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(field.getProject());
      final PsiClass holder = elementFactory.createClassFromText(text, field).getInnerClasses()[0];
      final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
      if (method == null) {
        return;
      }
      final PsiClass holderClass = (PsiClass)method.getParent().addBefore(holder, method);

      final PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(expression, PsiIfStatement.class);
      if (ifStatement != null) {
        ifStatement.delete();
      }

      final PsiExpression holderReference = elementFactory.createExpressionFromText(holderName + "." + field.getName(), field);
      for (PsiReference reference : ReferencesSearch.search(field).findAll()) {
        reference.getElement().replace(holderReference);
      }
      field.delete();

      if (!isOnTheFly()) {
        return;
      }
      invokeInplaceRename(holderClass, holderName, suggestHolderName(field));
    }

    private static void invokeInplaceRename(PsiNameIdentifierOwner nameIdentifierOwner, final String... suggestedNames) {
      final PsiNameIdentifierOwner elementToRename = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(nameIdentifierOwner);
      final Editor editor = FileEditorManager.getInstance(nameIdentifierOwner.getProject()).getSelectedTextEditor();
      if (editor == null) {
        return;
      }
      final PsiElement identifier = elementToRename.getNameIdentifier();
      if (identifier == null) {
        return;
      }
      editor.getCaretModel().moveToOffset(identifier.getTextOffset());
      final RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(elementToRename);
      if (!processor.isInplaceRenameSupported()) {
        return;
      }
      processor.substituteElementToRename(elementToRename, editor, new Pass<PsiElement>() {
        @Override
        public void pass(PsiElement substitutedElement) {
          final MemberInplaceRenamer renamer = new MemberInplaceRenamer(elementToRename, substitutedElement, editor);
          final LinkedHashSet<String> nameSuggestions = new LinkedHashSet<>(Arrays.asList(suggestedNames));
          renamer.performInplaceRefactoring(nameSuggestions);
        }
      });
    }

    @NonNls
    private static String suggestHolderName(PsiField field) {
      String string = field.getType().getDeepComponentType().getPresentableText();
      final int index = string.indexOf('<');
      if (index != -1) {
        string = string.substring(0, index);
      }
      return string + "Holder";
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("introduce.holder.class.quickfix");
    }
  }

  private static class UnsafeSafeLazyInitializationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(
      @NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      final PsiExpression lhs = expression.getLExpression();
      if (!(lhs instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression reference = (PsiReferenceExpression)lhs;
      final PsiElement referent = reference.resolve();
      if (!(referent instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)referent;
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      if (isInStaticInitializer(expression)) {
        return;
      }
      if (isInSynchronizedContext(expression)) {
        return;
      }
      final PsiStatement statement = PsiTreeUtil.getParentOfType(expression, PsiStatement.class);
      final PsiElement parent =
        PsiTreeUtil.skipParentsOfType(statement, PsiCodeBlock.class, PsiBlockStatement.class);
      if (!(parent instanceof PsiIfStatement)) {
        return;
      }
      final PsiIfStatement ifStatement = (PsiIfStatement)parent;
      final PsiExpression condition = ifStatement.getCondition();
      if (condition == null|| !ComparisonUtils.isNullComparison(condition, field, true)) {
        return;
      }
      registerError(lhs, ifStatement, field);
    }

    private static boolean isInSynchronizedContext(PsiElement element) {
      final PsiSynchronizedStatement syncBlock =
        PsiTreeUtil.getParentOfType(element,
                                    PsiSynchronizedStatement.class);
      if (syncBlock != null) {
        return true;
      }
      final PsiMethod method =
        PsiTreeUtil.getParentOfType(element,
                                    PsiMethod.class);
      return method != null &&
             method.hasModifierProperty(PsiModifier.SYNCHRONIZED)
             && method.hasModifierProperty(PsiModifier.STATIC);
    }

    private static boolean isInStaticInitializer(PsiElement element) {
      final PsiClassInitializer initializer =
        PsiTreeUtil.getParentOfType(element,
                                    PsiClassInitializer.class);
      return initializer != null &&
             initializer.hasModifierProperty(PsiModifier.STATIC);
    }
  }
}
