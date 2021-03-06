// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.initialization;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
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
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("non.thread.safe.lazy.initialization.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnsafeSafeLazyInitializationVisitor();
  }

  private static boolean isStaticAndAssignedOnce(PsiField field) {
    if (!field.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    final PsiClass containingClass = PsiUtil.getTopLevelClass(field);
    final int[] writeCount = new int[1];
    return ReferencesSearch.search(field).forEach(reference -> {
      final PsiElement element = reference.getElement();
      if (!PsiTreeUtil.isAncestor(containingClass, element, true)) {
        return false;
      }
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
    final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getLExpression());
    if (!(lhs instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
    if (!field.equals(referenceExpression.resolve())) {
      return false;
    }
    final boolean safe = PsiTreeUtil.processElements(assignmentExpression.getRExpression(), PsiReferenceExpression.class, ref -> {
      final PsiElement target = ref.resolve();
      return !(target instanceof PsiLocalVariable) && !(target instanceof PsiParameter);
    });
    if (!safe) {
      return false;
    }
    PsiElement[] elements = {assignmentExpression.getRExpression()};
    final HashSet<PsiField> usedFields = new HashSet<>();
    final PsiClass targetClass = field.getContainingClass();
    return ExtractMethodProcessor.canBeStatic(targetClass, expressionStatement, elements, usedFields) && usedFields.isEmpty();
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
      final String fieldName = field.getName();
      @NonNls final String holderName = StringUtil.capitalize(fieldName) + "Holder";
      final PsiElement expressionParent = expression.getParent();
      if (!(expressionParent instanceof PsiAssignmentExpression)) {
        return;
      }
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expressionParent;
      final PsiExpression rhs = assignmentExpression.getRExpression();
      if (rhs == null) {
        return;
      }
      @NonNls final String text = "private static final class " + holderName + " {}";
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(field.getProject());
      final PsiClass holder = elementFactory.createClassFromText(text, field).getInnerClasses()[0];
      final PsiMember method = PsiTreeUtil.getParentOfType(expression, PsiMember.class);
      if (method == null) {
        return;
      }
      final PsiClass holderClass = (PsiClass)method.getParent().addBefore(holder, method);
      final PsiField newField = (PsiField)holderClass.add(field);
      final PsiModifierList modifierList = newField.getModifierList();
      assert modifierList != null;
      modifierList.setModifierProperty(PsiModifier.FINAL, true);
      if (PsiUtil.isLanguageLevel11OrHigher(holderClass)) {
        modifierList.setModifierProperty(PsiModifier.PACKAGE_LOCAL, true);
      }
      newField.setInitializer(rhs);
      CodeStyleManager.getInstance(project).reformat(holderClass);

      final PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(expression, PsiIfStatement.class);
      if (ifStatement != null) {
        new CommentTracker().deleteAndRestoreComments(ifStatement);
      }

      final PsiExpression holderReference = elementFactory.createExpressionFromText(holderName + "." + fieldName, field);
      for (PsiReference reference : ReferencesSearch.search(field).findAll()) {
        reference.getElement().replace(holderReference);
      }
      field.delete();

      if (isOnTheFly()) {
        invokeInplaceRename(holderClass, holderName, suggestHolderName(field));
      }
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
      processor.substituteElementToRename(elementToRename, editor, new Pass<>() {
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

  private static class UnsafeSafeLazyInitializationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(expression.getLExpression());
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
      final PsiElement parent = PsiTreeUtil.skipParentsOfType(statement, PsiCodeBlock.class, PsiBlockStatement.class);
      if (!(parent instanceof PsiIfStatement)) {
        return;
      }
      final PsiIfStatement ifStatement = (PsiIfStatement)parent;
      final PsiExpression condition = ifStatement.getCondition();
      if (!ComparisonUtils.isNullComparison(condition, field, true)) {
        return;
      }
      registerError(lhs, ifStatement, field);
    }

    private static boolean isInSynchronizedContext(PsiElement element) {
      final PsiSynchronizedStatement syncBlock = PsiTreeUtil.getParentOfType(element, PsiSynchronizedStatement.class);
      if (syncBlock != null) {
        return true;
      }
      final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      return method != null && method.hasModifierProperty(PsiModifier.SYNCHRONIZED) && method.hasModifierProperty(PsiModifier.STATIC);
    }

    private static boolean isInStaticInitializer(PsiElement element) {
      final PsiClassInitializer initializer = PsiTreeUtil.getParentOfType(element, PsiClassInitializer.class);
      return initializer != null && initializer.hasModifierProperty(PsiModifier.STATIC);
    }
  }
}
