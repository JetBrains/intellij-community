/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.naming;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

public class NonExceptionNameEndsWithExceptionInspection extends NonExceptionNameEndsWithExceptionInspectionBase {

  @Override
  @NotNull
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final String name = (String)infos[0];
    final Boolean onTheFly = (Boolean)infos[1];
    if (onTheFly.booleanValue()) {
      return new InspectionGadgetsFix[]{new RenameFix(),
        new ExtendExceptionFix(name)};
    }
    else {
      return new InspectionGadgetsFix[]{
        new ExtendExceptionFix(name)};
    }
  }

  private static class ExtendExceptionFix extends InspectionGadgetsFix {

    private final String name;

    ExtendExceptionFix(String name) {
      this.name = name;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "non.exception.name.ends.with.exception.quickfix", name);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Make class extend 'Exception'";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)parent;
      final PsiReferenceList extendsList = aClass.getExtendsList();
      if (extendsList == null) {
        return;
      }
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = facade.getElementFactory();
      final GlobalSearchScope scope = aClass.getResolveScope();
      final PsiJavaCodeReferenceElement reference =
        factory.createReferenceElementByFQClassName(CommonClassNames.JAVA_LANG_EXCEPTION, scope);
      CommentTracker tracker = new CommentTracker();
      final PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
        tracker.delete(referenceElement);
      }
      tracker.insertCommentsBefore(extendsList.add(reference));
    }
  }
}