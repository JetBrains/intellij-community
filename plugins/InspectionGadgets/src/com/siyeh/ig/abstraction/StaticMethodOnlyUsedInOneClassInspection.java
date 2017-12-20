/*
 * Copyright 2006-2017 Bas Leijdekkers
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

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.RefactoringInspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicReference;

public class StaticMethodOnlyUsedInOneClassInspection extends BaseGlobalInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreTestClasses = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreAnonymousClasses = true;

  @SuppressWarnings("PublicField")
  public boolean ignoreOnConflicts = true;

  static final Key<SmartPsiElementPointer<PsiClass>> MARKER = Key.create("STATIC_METHOD_USED_IN_ONE_CLASS");

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("static.method.only.used.in.one.class.display.name");
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

  @Nullable
  @Override
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity,
                                                @NotNull AnalysisScope scope,
                                                @NotNull InspectionManager manager,
                                                @NotNull GlobalInspectionContext globalContext) {
    if (!(refEntity instanceof RefMethod)) {
      return null;
    }
    final RefMethod method = (RefMethod)refEntity;
    if (!method.isStatic() || method.getAccessModifier() == PsiModifier.PRIVATE) {
      return null;
    }
    RefClass usageClass = null;
    for (RefElement reference : method.getInReferences()) {
      final RefClass ownerClass = RefJavaUtil.getInstance().getOwnerClass(reference);
      if (usageClass == null) {
        usageClass = ownerClass;
      }
      else if (usageClass != ownerClass) {
        return null;
      }
    }
    final RefClass containingClass = method.getOwnerClass();
    if (usageClass == containingClass) {
      return null;
    }
    if (usageClass == null) {
      final PsiClass aClass = containingClass.getElement();
      if (aClass != null) {
        final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(manager.getProject());
        method.putUserData(MARKER, smartPointerManager.createSmartPsiElementPointer(aClass));
      }
      return null;
    }
    if (ignoreAnonymousClasses && (usageClass.isAnonymous() || usageClass.isLocalClass() ||
                                   usageClass.getOwner() instanceof RefClass && !usageClass.isStatic())) {
      return null;
    }
    if (ignoreTestClasses && usageClass.isTestCase()) {
      return null;
    }
    final PsiClass psiClass = usageClass.getElement();
    if (psiClass == null) {
      return null;
    }
    final PsiMethod psiMethod = (PsiMethod)method.getElement();
    if (psiMethod == null) {
      return null;
    }
    if (ignoreOnConflicts) {
      if (psiClass.findMethodsBySignature(psiMethod, true).length > 0 || !areReferenceTargetsAccessible(psiMethod, psiClass)) {
        return null;
      }
    }
    final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(manager.getProject());
    method.putUserData(MARKER, smartPointerManager.createSmartPsiElementPointer(psiClass));
    return new ProblemDescriptor[]{createProblemDescriptor(manager, psiMethod.getNameIdentifier(), psiClass)};
  }

  @NotNull
  static ProblemDescriptor createProblemDescriptor(@NotNull InspectionManager manager, PsiElement problemElement, PsiClass usageClass) {
    final String message = (usageClass instanceof PsiAnonymousClass)
                           ? InspectionGadgetsBundle.message("static.method.only.used.in.one.anonymous.class.problem.descriptor",
                                                             ((PsiAnonymousClass)usageClass).getBaseClassReference().getText())
                           : InspectionGadgetsBundle.message("static.method.only.used.in.one.class.problem.descriptor", usageClass.getName());
    return manager.createProblemDescriptor(problemElement, message, false, null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  @Override
  public boolean queryExternalUsagesRequests(@NotNull final InspectionManager manager,
                                             @NotNull final GlobalInspectionContext globalContext,
                                             @NotNull final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    globalContext.getRefManager().iterate(new RefJavaVisitor() {
      @Override public void visitElement(@NotNull RefEntity refEntity) {
        if (refEntity instanceof RefMethod ) {
          final SmartPsiElementPointer<PsiClass> classPointer = refEntity.getUserData(MARKER);
          if (classPointer != null) {
            final Ref<PsiClass> ref = Ref.create(classPointer.getElement());
            final GlobalJavaInspectionContext globalJavaContext = globalContext.getExtension(GlobalJavaInspectionContext.CONTEXT);
            globalJavaContext.enqueueMethodUsagesProcessor((RefMethod)refEntity, new GlobalJavaInspectionContext.UsagesProcessor() {
              @Override
              public boolean process(PsiReference reference) {
                final PsiClass containingClass = ClassUtils.getContainingClass(reference.getElement());
                if (problemDescriptionsProcessor.getDescriptions(refEntity) != null) {
                  if (containingClass != ref.get()) {
                    problemDescriptionsProcessor.ignoreElement(refEntity);
                    return false;
                  }
                  return true;
                }
                else {
                  final PsiIdentifier identifier = ((PsiMethod)((RefMethod)refEntity).getElement()).getNameIdentifier();
                  final ProblemDescriptor problemDescriptor = createProblemDescriptor(manager, identifier, containingClass);
                  problemDescriptionsProcessor.addProblemElement(refEntity, problemDescriptor);
                  ref.set(containingClass);
                  return true;
                }
              }
            });
          }
        }
      }
    });

    return false;
  }

  static boolean areReferenceTargetsAccessible(final PsiElement elementToCheck, final PsiElement place) {
    final AccessibleVisitor visitor = new AccessibleVisitor(elementToCheck, place);
    elementToCheck.accept(visitor);
    return visitor.isAccessible();
  }

  private static class AccessibleVisitor extends JavaRecursiveElementWalkingVisitor {
    private final PsiElement myElementToCheck;
    private final PsiElement myPlace;
    private boolean myAccessible = true;

    public AccessibleVisitor(PsiElement elementToCheck, PsiElement place) {
      myElementToCheck = elementToCheck;
      myPlace = place;
    }

    @Override
    public void visitCallExpression(PsiCallExpression callExpression) {
      if (!myAccessible) {
        return;
      }
      super.visitCallExpression(callExpression);
      final PsiMethod method = callExpression.resolveMethod();
      if (callExpression instanceof PsiNewExpression && method == null) {
        final PsiNewExpression newExpression = (PsiNewExpression)callExpression;
        final PsiJavaCodeReferenceElement reference = newExpression.getClassReference();
        if (reference != null) {
          checkElement(reference.resolve());
        }
      }
      else {
        checkElement(method);
      }
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      if (!myAccessible) {
        return;
      }
      super.visitReferenceExpression(expression);
      checkElement(expression.resolve());
    }

    private void checkElement(PsiElement element) {
      if (!(element instanceof PsiMember)) {
        return;
      }
      if (PsiTreeUtil.isAncestor(myElementToCheck, element, false)) {
        return; // internal reference
      }
      myAccessible =  PsiUtil.isAccessible((PsiMember)element, myPlace, null);
    }

    public boolean isAccessible() {
      return myAccessible;
    }
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
    public PsiClass findUsageClass(final PsiMethod method) {
      ProgressManager.getInstance().runProcess(() -> {
        final Query<PsiReference> query = MethodReferencesSearch.search(method);
        if (!query.forEach(this)) {
          foundClass.set(null);
        }
      }, null);
      return foundClass.get();
    }
  }

  @Nullable
  @Override
  public LocalInspectionTool getSharedLocalInspectionTool() {
    return new StaticMethodOnlyUsedInOneClassLocalInspection(this);
  }

  private static class StaticMethodOnlyUsedInOneClassLocalInspection
    extends BaseSharedLocalInspection<StaticMethodOnlyUsedInOneClassInspection> {

    public StaticMethodOnlyUsedInOneClassLocalInspection(StaticMethodOnlyUsedInOneClassInspection settingsDelegate) {
      super(settingsDelegate);
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
      final PsiClass usageClass = (PsiClass)infos[0];
      return (usageClass instanceof PsiAnonymousClass)
             ? InspectionGadgetsBundle.message("static.method.only.used.in.one.anonymous.class.problem.descriptor",
                                               ((PsiAnonymousClass)usageClass).getBaseClassReference().getText())
             : InspectionGadgetsBundle.message("static.method.only.used.in.one.class.problem.descriptor", usageClass.getName());
    }

    @Override
    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
      final PsiClass usageClass = (PsiClass)infos[0];
      return new StaticMethodOnlyUsedInOneClassFix(usageClass);
    }

    private static class StaticMethodOnlyUsedInOneClassFix extends RefactoringInspectionGadgetsFix {

      private final SmartPsiElementPointer<PsiClass> usageClass;

      public StaticMethodOnlyUsedInOneClassFix(PsiClass usageClass) {
        final SmartPointerManager pointerManager = SmartPointerManager.getInstance(usageClass.getProject());
        this.usageClass = pointerManager.createSmartPsiElementPointer(usageClass);
      }

      @Override
      @NotNull
      public String getFamilyName() {
        return InspectionGadgetsBundle.message("static.method.only.used.in.one.class.quickfix");
      }

      @NotNull
      @Override
      public RefactoringActionHandler getHandler() {
        return RefactoringActionHandlerFactory.getInstance().createMoveHandler();
      }

      @NotNull
      @Override
      public DataContext enhanceDataContext(DataContext context) {
        return SimpleDataContext.getSimpleContext(LangDataKeys.TARGET_PSI_ELEMENT.getName(), usageClass.getElement(), context);
      }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
      return new StaticMethodOnlyUsedInOneClassVisitor();
    }

    private class StaticMethodOnlyUsedInOneClassVisitor extends BaseInspectionVisitor {

      @Override
      public void visitMethod(final PsiMethod method) {
        super.visitMethod(method);
        if (!method.hasModifierProperty(PsiModifier.STATIC) ||
            method.hasModifierProperty(PsiModifier.PRIVATE) ||
            method.getNameIdentifier() == null) {
          return;
        }
        if (DeclarationSearchUtils.isTooExpensiveToSearch(method, true)) {
          return;
        }
        final UsageProcessor usageProcessor = new UsageProcessor();
        final PsiClass usageClass = usageProcessor.findUsageClass(method);
        if (usageClass == null) {
          return;
        }
        final PsiClass containingClass = method.getContainingClass();
        if (usageClass.equals(containingClass)) {
          return;
        }
        if (mySettingsDelegate.ignoreTestClasses && TestUtils.isInTestCode(usageClass)) {
          return;
        }
        if (usageClass.getContainingClass() != null && !usageClass.hasModifierProperty(PsiModifier.STATIC) ||
            PsiUtil.isLocalOrAnonymousClass(usageClass)) {
          if (mySettingsDelegate.ignoreAnonymousClasses) {
            return;
          }
          if (PsiTreeUtil.isAncestor(containingClass, usageClass, true)) {
            return;
          }
        }
        if (mySettingsDelegate.ignoreOnConflicts) {
          if (usageClass.findMethodsBySignature(method, true).length > 0 || !areReferenceTargetsAccessible(method, usageClass)) {
            return;
          }
        }
        registerMethodError(method, usageClass);
      }
    }
  }
}
