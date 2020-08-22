// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;

public final class ImplementOrExtendFix extends BaseFix {
  private final SmartPsiElementPointer<PsiClass> myCompClassPointer;

  private ImplementOrExtendFix(@NotNull PsiClass compClass,
                               @NotNull PsiClass checkedClass,
                               boolean onTheFly) {
    super(checkedClass, onTheFly);
    myCompClassPointer = SmartPointerManager.createPointer(compClass);
  }

  public static LocalQuickFix @NotNull [] createFix(PsiClass compClass, PsiClass checkedClass, boolean onTheFly) {
    ImplementOrExtendFix fix = null;

    if (compClass.isInterface() && compClass.getImplementsList() != null) {
      fix = new ImplementOrExtendFix(compClass, checkedClass, onTheFly);
    }
    else if (!compClass.isInterface()) {
      PsiReferenceList extendsList = checkedClass.getExtendsList();
      if (extendsList != null) {
        if (extendsList.getReferenceElements().length == 0) {
          fix = new ImplementOrExtendFix(compClass, checkedClass, onTheFly);
        }
        else if (extendsList.getReferenceElements().length == 1) {
          // check for explicit "extends Object" case
          PsiClassType javaLangObject = PsiType.getJavaLangObject(checkedClass.getManager(),
                                                                  checkedClass.getResolveScope());
          if (extendsList.getReferencedTypes()[0].equals(javaLangObject)) {
            fix = new ImplementOrExtendFix(compClass, checkedClass, onTheFly);
          }
        }
      }
    }
    return fix != null ? new LocalQuickFix[]{fix} : LocalQuickFix.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public String getName() {
    PsiClass clazz = myCompClassPointer.getElement();
    if (clazz == null) return "Invalid";
    return (clazz.isInterface()
            ? StringUtil.capitalize(DevKitBundle.message("keyword.implement"))
            : StringUtil.capitalize(DevKitBundle.message("keyword.extend")))
           + " '" + clazz.getQualifiedName() + "'";
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return "Implement/Extend required base class";
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor, boolean external) throws IncorrectOperationException {
    PsiElement element = myPointer.getElement();
    PsiClass compClass = myCompClassPointer.getElement();
    if (!(element instanceof PsiClass)) return;
    if (compClass == null) return;
    PsiClass clazz = (PsiClass)element;
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(clazz.getProject()).getElementFactory();
    PsiClassType compType = elementFactory.createType(compClass);

    PsiReferenceList list;
    if (compClass.isInterface()) {
      list = clazz.getImplementsList();
      assert list != null;
    }
    else {
      PsiReferenceList extendsList = clazz.getExtendsList();
      assert extendsList != null;
      if (extendsList.getReferencedTypes().length > 0) {
        extendsList.getReferenceElements()[0].delete();
      }
      list = extendsList;
    }

    PsiElement e = list.add(elementFactory.createReferenceElementByType(compType));
    if (myOnTheFly && external && e instanceof Navigatable) ((Navigatable)e).navigate(true);
  }
}
