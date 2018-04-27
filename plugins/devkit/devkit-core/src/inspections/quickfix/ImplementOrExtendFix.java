/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import static org.jetbrains.idea.devkit.util.PsiUtil.createPointer;

public class ImplementOrExtendFix extends BaseFix {
  private final SmartPsiElementPointer<PsiClass> myCompClassPointer;

  private ImplementOrExtendFix(@NotNull PsiClass compClass,
                               @NotNull PsiClass checkedClass,
                               boolean onTheFly) {
    super(createPointer(checkedClass), onTheFly);
    myCompClassPointer = createPointer(compClass);
  }

  @NotNull
  public static LocalQuickFix[] createFix(PsiClass compClass, PsiClass checkedClass, boolean onTheFly) {
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

  @NotNull
  public String getName() {
    PsiClass clazz = myCompClassPointer.getElement();
    if (clazz == null) return "Invalid";
    return (clazz.isInterface()
            ? StringUtil.capitalize(DevKitBundle.message("keyword.implement"))
            : StringUtil.capitalize(DevKitBundle.message("keyword.extend")))
           + " '" + clazz.getQualifiedName() + "'";
  }

  @NotNull
  public String getFamilyName() {
    return "Implement/Extend required base class";
  }

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
