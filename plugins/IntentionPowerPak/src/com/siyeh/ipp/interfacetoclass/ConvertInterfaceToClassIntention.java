/*
 * Copyright 2006-2014 Bas Leijdekkers
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
package com.siyeh.ipp.interfacetoclass;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.containers.MultiMap;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.intellij.openapi.application.ApplicationManager.getApplication;

public class ConvertInterfaceToClassIntention extends Intention {

  private static void changeInterfaceToClass(PsiClass anInterface) throws IncorrectOperationException {
    final PsiIdentifier nameIdentifier = anInterface.getNameIdentifier();
    assert nameIdentifier != null;
    final PsiElement whiteSpace = nameIdentifier.getPrevSibling();
    assert whiteSpace != null;
    final PsiElement interfaceToken = whiteSpace.getPrevSibling();
    assert interfaceToken != null;
    final PsiKeyword interfaceKeyword = (PsiKeyword)interfaceToken.getOriginalElement();
    final Project project = anInterface.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = psiFacade.getElementFactory();
    final PsiKeyword classKeyword = factory.createKeyword("class");
    interfaceKeyword.replace(classKeyword);

    final PsiModifierList classModifierList = anInterface.getModifierList();
    if (classModifierList == null) {
      return;
    }
    classModifierList.setModifierProperty(PsiModifier.ABSTRACT, true);

    final PsiElement parent = anInterface.getParent();
    if (parent instanceof PsiClass) {
      classModifierList.setModifierProperty(PsiModifier.STATIC, true);
    }

    final PsiMethod[] methods = anInterface.getMethods();
    for (final PsiMethod method : methods) {
      PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, true);
      if (method.hasModifierProperty(PsiModifier.DEFAULT)) {
        PsiUtil.setModifierProperty(method, PsiModifier.DEFAULT, false);
      }
      else if (!method.hasModifierProperty(PsiModifier.STATIC)) {
        PsiUtil.setModifierProperty(method, PsiModifier.ABSTRACT, true);
      }
    }

    final PsiField[] fields = anInterface.getFields();
    for (final PsiField field : fields) {
      final PsiModifierList modifierList = field.getModifierList();
      if (modifierList != null) {
        modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
        modifierList.setModifierProperty(PsiModifier.STATIC, true);
        modifierList.setModifierProperty(PsiModifier.FINAL, true);
      }
    }

    final PsiClass[] innerClasses = anInterface.getInnerClasses();
    for (PsiClass innerClass : innerClasses) {
      final PsiModifierList modifierList = innerClass.getModifierList();
      if (modifierList != null) {
        modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
        if (!innerClass.isInterface()) {
          modifierList.setModifierProperty(PsiModifier.STATIC, true);
        }
      }
    }
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    final PsiClass anInterface = (PsiClass)element.getParent();
    final SearchScope searchScope = anInterface.getUseScope();
    final Query<PsiClass> query = ClassInheritorsSearch.search(anInterface, searchScope, false);
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    query.forEach(aClass -> {
      final PsiReferenceList extendsList = aClass.getExtendsList();
      if (extendsList == null) {
        return true;
      }
      final PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
      if (referenceElements.length > 0) {
        final PsiElement target = referenceElements[0].resolve();
        if (target != null) {
          conflicts.putValue(aClass, IntentionPowerPackBundle.message(
            "0.already.extends.1.and.will.not.compile.after.converting.2.to.a.class",
            RefactoringUIUtil.getDescription(aClass, true), RefactoringUIUtil.getDescription(target, true),
            RefactoringUIUtil.getDescription(anInterface, false)));
        }
      }
      return true;
    });

    final PsiFunctionalExpression functionalExpression = FunctionalExpressionSearch.search(anInterface, searchScope).findFirst();
    if (functionalExpression != null) {
      final String conflictMessage = ClassPresentationUtil.getFunctionalExpressionPresentation(functionalExpression, true) +
                           " will not compile after converting " + RefactoringUIUtil.getDescription(anInterface, false) + " to a class";
      conflicts.putValue(functionalExpression, conflictMessage);
    }
    final boolean conflictsDialogOK;
    if (conflicts.isEmpty()) {
      conflictsDialogOK = true;
    } else {
      if (getApplication().isUnitTestMode()) {
        throw new BaseRefactoringProcessor.ConflictsInTestsException(conflicts.values());
      }
      final ConflictsDialog conflictsDialog = new ConflictsDialog(anInterface.getProject(), conflicts,
                                                                  () -> ApplicationManager.getApplication().runWriteAction(() -> convertInterfaceToClass(anInterface)));
      conflictsDialogOK = conflictsDialog.showAndGet();
    }
    if (conflictsDialogOK) {
      convertInterfaceToClass(anInterface);
    }
  }

  private static void convertInterfaceToClass(PsiClass anInterface) {
    final boolean success = moveSubClassImplementsToExtends(anInterface);
    if (!success) {
      return;
    }
    changeInterfaceToClass(anInterface);
    moveExtendsToImplements(anInterface);
  }

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new ConvertInterfaceToClassPredicate();
  }

  private static void moveExtendsToImplements(PsiClass anInterface) throws IncorrectOperationException {
    final PsiReferenceList extendsList = anInterface.getExtendsList();
    final PsiReferenceList implementsList = anInterface.getImplementsList();
    assert extendsList != null;
    final PsiJavaCodeReferenceElement[] extendsRefElements = extendsList.getReferenceElements();
    for (PsiJavaCodeReferenceElement referenceElement : extendsRefElements) {
      assert implementsList != null;
      final PsiElement resolved = referenceElement.resolve();
      if (resolved instanceof PsiClass && ((PsiClass)resolved).isInterface()) {
        implementsList.add(referenceElement);
        referenceElement.delete();
      }
    }
  }

  private static boolean moveSubClassImplementsToExtends(PsiClass oldInterface) throws IncorrectOperationException {
    final Project project = oldInterface.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory elementFactory = psiFacade.getElementFactory();
    final PsiJavaCodeReferenceElement oldInterfaceReference = elementFactory.createClassReferenceElement(oldInterface);
    final SearchScope searchScope = oldInterface.getUseScope();
    final Query<PsiClass> query = ClassInheritorsSearch.search(oldInterface, searchScope, false);
    final Collection<PsiClass> inheritors = query.findAll();
    final boolean success = CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, inheritors, false);
    if (!success) {
      return false;
    }
    for (PsiClass inheritor : inheritors) {
      final PsiReferenceList implementsList = inheritor.getImplementsList();
      final PsiReferenceList extendsList = inheritor.getExtendsList();
      if (implementsList != null) {
        moveReference(implementsList, extendsList, oldInterfaceReference);
      }
    }
    return true;
  }

  private static void moveReference(@NotNull PsiReferenceList source, @Nullable PsiReferenceList target,
                                    @NotNull PsiJavaCodeReferenceElement reference) throws IncorrectOperationException {
    final PsiJavaCodeReferenceElement[] implementsReferences = source.getReferenceElements();
    final String qualifiedName = reference.getQualifiedName();
    for (PsiJavaCodeReferenceElement implementsReference : implementsReferences) {
      final String implementsReferenceQualifiedName = implementsReference.getQualifiedName();
      if (qualifiedName.equals(implementsReferenceQualifiedName)) {
        if (target != null) {
          target.add(implementsReference);
        }
        implementsReference.delete();
      }
    }
  }
}