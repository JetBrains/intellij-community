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
package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicReference;

public class StaticMethodOnlyUsedInOneClassInspectionBase extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreTestClasses = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreAnonymousClasses = true;

  @SuppressWarnings("PublicField")
  public boolean ignoreOnConflicts = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("static.method.only.used.in.one.class.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiNamedElement element = (PsiNamedElement)infos[0];
    final String name = element.getName();
    if (infos.length > 1) {
      if (Boolean.TRUE.equals(infos[1])) {
        return InspectionGadgetsBundle.message("static.method.only.used.in.one.class.problem.descriptor.anonymous.extending", name);
      }
      return InspectionGadgetsBundle.message("static.method.only.used.in.one.class.problem.descriptor.anonymous.implementing", name);
    }
    return InspectionGadgetsBundle.message("static.method.only.used.in.one.class.problem.descriptor", name);
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("static.method.only.used.in.one.class.ignore.test.option"), "ignoreTestClasses");
    panel.addCheckbox(InspectionGadgetsBundle.message("static.method.only.used.in.one.class.ignore.anonymous.option"),
                      "ignoreAnonymousClasses");
    panel.addCheckbox(InspectionGadgetsBundle.message("static.method.only.used.in.one.class.ignore.on.conflicts"), "ignoreOnConflicts");
    return panel;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StaticMethodOnlyUsedInOneClassVisitor();
  }

  private static class UsageProcessor implements Processor<PsiReference> {

    private final AtomicReference<PsiClass> foundClass = new AtomicReference<>();

    @Override
    public boolean process(PsiReference reference) {
      ProgressManager.checkCanceled();
      final PsiElement element = reference.getElement();
      final PsiClass usageClass = ClassUtils.getContainingClass(element);
      if (usageClass == null) {
        return true;
      }
      if (foundClass.compareAndSet(null, usageClass)) {
        return true;
      }
      final PsiClass aClass = foundClass.get();
      final PsiManager manager = usageClass.getManager();
      return manager.areElementsEquivalent(aClass, usageClass);
    }

    /**
     * @return the class the specified method is used from, or null if it is
     *         used from 0 or more than 1 other classes.
     */
    @Nullable
    public PsiClass getUsageClass(final PsiMethod method) {
      if (DeclarationSearchUtils.isTooExpensiveToSearch(method, true)) {
        return null;
      }
      ProgressManager.getInstance().runProcess(() -> {
        final Query<PsiReference> query = MethodReferencesSearch.search(method);
        if (!query.forEach(this)) {
          foundClass.set(null);
        }
      }, null);
      return foundClass.get();
    }
  }

  private class StaticMethodOnlyUsedInOneClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(final PsiMethod method) {
      super.visitMethod(method);
      if (!method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      if (method.getNameIdentifier() == null) {
        return;
      }
      final UsageProcessor usageProcessor = new UsageProcessor();
      final PsiClass usageClass = usageProcessor.getUsageClass(method);
      if (usageClass == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (usageClass.equals(containingClass)) {
        return;
      }
      if (ignoreOnConflicts) {
        if (usageClass.findMethodsBySignature(method, true).length > 0 || !areReferenceTargetsAccessible(method, usageClass)) {
          return;
        }
      }
      if (ignoreTestClasses && TestUtils.isInTestCode(usageClass)) {
        return;
      }
      if (usageClass instanceof PsiAnonymousClass) {
        if (ignoreAnonymousClasses) {
          return;
        }
        if (PsiTreeUtil.isAncestor(containingClass, usageClass, true)) {
          return;
        }
        final PsiClass[] interfaces = usageClass.getInterfaces();
        final PsiClass superClass;
        if (interfaces.length == 1) {
          superClass = interfaces[0];
          registerMethodError(method, superClass, Boolean.FALSE);
        }
        else {
          superClass = usageClass.getSuperClass();
          if (superClass == null) {
            return;
          }
          registerMethodError(method, superClass, Boolean.TRUE);
        }
      }
      else {
        registerMethodError(method, usageClass);
      }
    }
  }

  private static boolean areReferenceTargetsAccessible(final PsiElement elementToCheck, final PsiElement place) {
    return PsiTreeUtil.processElements(elementToCheck, new PsiElementProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element) {
        if (!(element instanceof PsiJavaCodeReferenceElement)) {
          return true;
        }
        final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)element;
        final PsiElement target = referenceElement.resolve();
        if (target == null) {
          return false; // broken code
        }
        if (PsiTreeUtil.isAncestor(elementToCheck, target, false)) {
          return true; // internal reference
        }
        return target instanceof PsiMember && PsiUtil.isAccessible((PsiMember)target, place, null);
      }
    });
  }
}
