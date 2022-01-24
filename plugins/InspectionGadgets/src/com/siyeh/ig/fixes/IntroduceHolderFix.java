// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;

/**
 * @author Bas Leijdekkers
 */
public class IntroduceHolderFix extends InspectionGadgetsFix {

  private IntroduceHolderFix() {}

  public static IntroduceHolderFix createFix(PsiField field, PsiIfStatement ifStatement) {
    if (!isStaticAndAssignedOnce(field) || !isSafeToDeleteIfStatement(ifStatement, field)) {
      return null;
    }
    return new IntroduceHolderFix();
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiReferenceExpression referenceExpression;
    final PsiIfStatement ifStatement;
    if (element instanceof PsiKeyword) {
      // double-checked locking
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiIfStatement)) {
        return;
      }
      ifStatement = (PsiIfStatement)parent;
      final PsiIfStatement innerIfStatement = getDoubleCheckedLockingInnerIf(ifStatement);
      if (innerIfStatement == null) {
        return;
      }
      final PsiStatement thenBranch2 = ControlFlowUtils.stripBraces(innerIfStatement.getThenBranch());
      if (!(thenBranch2 instanceof PsiExpressionStatement)) {
        return;
      }
      final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)thenBranch2;
      final PsiExpression expression = expressionStatement.getExpression();
      if (!(expression instanceof PsiAssignmentExpression)) {
        return;
      }
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getLExpression());
      if (!(lhs instanceof PsiReferenceExpression)) {
        return;
      }
      referenceExpression = (PsiReferenceExpression)lhs;
    } else {
      referenceExpression = (PsiReferenceExpression)element;
      ifStatement = PsiTreeUtil.getParentOfType(referenceExpression, PsiIfStatement.class);
    }
    replaceWithStaticHolder(referenceExpression, ifStatement);
  }

  public static PsiIfStatement getDoubleCheckedLockingInnerIf(PsiIfStatement ifStatement) {
    final PsiStatement thenBranch = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
    if (!(thenBranch instanceof PsiSynchronizedStatement)) {
      return null;
    }
    final PsiSynchronizedStatement synchronizedStatement = (PsiSynchronizedStatement)thenBranch;
    final PsiCodeBlock body = synchronizedStatement.getBody();
    final PsiStatement statement = ControlFlowUtils.getOnlyStatementInBlock(body);
    return (statement instanceof PsiIfStatement) ? (PsiIfStatement)statement : null;
  }

  private void replaceWithStaticHolder(PsiReferenceExpression referenceExpression, PsiIfStatement ifStatement) {
    final PsiElement resolved = referenceExpression.resolve();
    if (!(resolved instanceof PsiField)) {
      return;
    }
    final PsiField field = (PsiField)resolved;
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(field.getProject());
    final String fieldName = field.getName();
    @NonNls final String holderName =
      StringUtil.capitalize(codeStyleManager.variableNameToPropertyName(fieldName, VariableKind.STATIC_FINAL_FIELD)) + "Holder";
    final PsiElement expressionParent = referenceExpression.getParent();
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
    final PsiMember method = PsiTreeUtil.getParentOfType(referenceExpression, PsiMember.class);
    if (method == null) {
      return;
    }
    final PsiClass holderClass = (PsiClass)method.getParent().addBefore(holder, method);
    final PsiField newField = (PsiField)holderClass.add(field);
    final PsiModifierList modifierList = newField.getModifierList();
    assert modifierList != null;
    modifierList.setModifierProperty(PsiModifier.FINAL, true);
    if (!PsiUtil.isLanguageLevel11OrHigher(holderClass)) {
      modifierList.setModifierProperty(PsiModifier.PACKAGE_LOCAL, true);
    }
    newField.setInitializer(rhs);
    CodeStyleManager.getInstance(referenceExpression.getProject()).reformat(holderClass);

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
    final PsiExpression rhs = assignmentExpression.getRExpression();
    final boolean safe = PsiTreeUtil.processElements(rhs, PsiReferenceExpression.class, ref -> {
      final PsiElement target = ref.resolve();
      return !(target instanceof PsiLocalVariable) && !(target instanceof PsiParameter);
    });
    if (!safe) {
      return false;
    }
    final PsiElement[] elements = rhs == null ? PsiElement.EMPTY_ARRAY : new PsiElement[] {rhs};
    final HashSet<PsiField> usedFields = new HashSet<>();
    final PsiClass targetClass = field.getContainingClass();
    return JavaSpecialRefactoringProvider.getInstance()
             .canBeStatic(targetClass, expressionStatement, elements, usedFields) && usedFields.isEmpty();
  }
}
