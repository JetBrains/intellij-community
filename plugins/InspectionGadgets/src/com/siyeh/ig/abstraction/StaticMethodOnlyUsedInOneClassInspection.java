// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.BaseSharedLocalInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RefactoringInspectionGadgetsFix;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UElement;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicReference;

public class StaticMethodOnlyUsedInOneClassInspection extends BaseGlobalInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreTestClasses = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreAnonymousClasses = true;

  @SuppressWarnings("PublicField")
  public boolean ignoreOnConflicts = true;

  @SuppressWarnings("PublicField")
  public boolean ignoreUtilityClasses = true;

  static final Key<SmartPsiElementPointer<PsiClass>> MARKER = Key.create("STATIC_METHOD_USED_IN_ONE_CLASS");

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("static.method.only.used.in.one.class.ignore.test.option"), "ignoreTestClasses");
    panel.addCheckbox(InspectionGadgetsBundle.message("static.method.only.used.in.one.class.ignore.anonymous.option"), "ignoreAnonymousClasses");
    panel.addCheckbox(InspectionGadgetsBundle.message("static.method.only.used.in.one.class.ignore.on.conflicts"), "ignoreOnConflicts");
    panel.addCheckbox(InspectionGadgetsBundle.message("static.method.only.used.in.one.class.ignore.utility.classes"), "ignoreUtilityClasses");
    return panel;
  }

  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(@NotNull RefEntity refEntity,
                                                           @NotNull AnalysisScope scope,
                                                           @NotNull InspectionManager manager,
                                                           @NotNull GlobalInspectionContext globalContext) {
    if (!(refEntity instanceof RefMethod) && !(refEntity instanceof RefField)) {
      return null;
    }
    final RefJavaElement element = (RefJavaElement)refEntity;
    if (!element.isStatic() || element.getAccessModifier() == PsiModifier.PRIVATE) {
      return null;
    }
    RefClass usageClass = null;
    for (RefElement reference : element.getInReferences()) {
      final RefClass ownerClass = getOwnerClass(reference);
      if (usageClass == null) {
        usageClass = ownerClass;
      }
      else if (usageClass != ownerClass) {
        return null;
      }
    }
    final RefClass containingClass = element instanceof RefMethod
                                     ? ((RefMethod)element).getOwnerClass()
                                     : ((RefField)element).getOwnerClass();
    if (containingClass == null || usageClass == containingClass) {
      return null;
    }
    if (usageClass == null) {
      final PsiClass aClass = ObjectUtils.tryCast(containingClass.getPsiElement(), PsiClass.class);
      if (aClass != null) {
        final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(manager.getProject());
        element.putUserData(MARKER, smartPointerManager.createSmartPsiElementPointer(aClass));
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
    if (ignoreUtilityClasses && containingClass.isUtilityClass()) {
      // RefClass.isUtilityClass() is also true for enums
      return null;
    }
    final PsiClass psiClass = ObjectUtils.tryCast(usageClass.getPsiElement(), PsiClass.class);
    if (psiClass == null) {
      return null;
    }
    if (element instanceof RefMethod) {
      final PsiMethod method = ObjectUtils.tryCast(element.getPsiElement(), PsiMethod.class);
      if (method == null || MethodUtils.isFactoryMethod(method) || MethodUtils.isConvenienceOverload(method)) {
        return null;
      }
      if (ignoreOnConflicts) {
        if (psiClass.findMethodsBySignature(method, true).length > 0 || !areReferenceTargetsAccessible(method, psiClass)) {
          return null;
        }
      }
      final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(manager.getProject());
      element.putUserData(MARKER, smartPointerManager.createSmartPsiElementPointer(psiClass));
      return new ProblemDescriptor[]{createProblemDescriptor(manager, method.getNameIdentifier(), psiClass)};
    }
    else {
      final PsiField field = ObjectUtils.tryCast(element.getPsiElement(), PsiField.class);
      if (field == null || field instanceof PsiEnumConstant || isSingletonField(field)) {
        return null;
      }
      if (ignoreOnConflicts) {
        if (psiClass.findFieldByName(element.getName(), true) != null || !areReferenceTargetsAccessible(field, psiClass)) {
          return null;
        }
      }
      final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(manager.getProject());
      element.putUserData(MARKER, smartPointerManager.createSmartPsiElementPointer(psiClass));
      return new ProblemDescriptor[]{createProblemDescriptor(manager, field.getNameIdentifier(), psiClass)};
    }
  }

  @Nullable
  private static RefClass getOwnerClass(RefEntity element) {
    while (!(element instanceof RefClass) && element instanceof RefJavaElement) {
      element = element.getOwner();
    }
    return (element instanceof RefClass) ? (RefClass)element : null;
  }

  @NotNull
  static ProblemDescriptor createProblemDescriptor(@NotNull InspectionManager manager, PsiElement problemElement, PsiClass usageClass) {
    final String message = (usageClass instanceof PsiAnonymousClass)
                           ? InspectionGadgetsBundle.message("static.method.only.used.in.one.anonymous.class.problem.descriptor",
                                                             (problemElement.getParent() instanceof PsiMethod) ? 1 : 2,
                                                             ((PsiAnonymousClass)usageClass).getBaseClassReference().getText())
                           : InspectionGadgetsBundle.message("static.method.only.used.in.one.class.problem.descriptor",
                                                             (problemElement.getParent() instanceof PsiMethod) ? 1 : 2,
                                                             usageClass.getName());
    return manager.createProblemDescriptor(problemElement, message, false, null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  @Override
  public boolean queryExternalUsagesRequests(@NotNull final InspectionManager manager,
                                             @NotNull final GlobalInspectionContext globalContext,
                                             @NotNull final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    globalContext.getRefManager().iterate(new RefJavaVisitor() {
      @Override public void visitElement(@NotNull RefEntity refEntity) {
        final SmartPsiElementPointer<PsiClass> classPointer = refEntity.getUserData(MARKER);
        if (classPointer != null) {
          final Ref<PsiClass> ref = Ref.create(classPointer.getElement());
          final GlobalJavaInspectionContext globalJavaContext = globalContext.getExtension(GlobalJavaInspectionContext.CONTEXT);
            final GlobalJavaInspectionContext.UsagesProcessor processor = new GlobalJavaInspectionContext.UsagesProcessor() {
              @Override
              public boolean process(PsiReference reference) {
                final PsiClass containingClass = ClassUtils.getContainingClass(reference.getElement());
                if (containingClass == null) return false;
                if (problemDescriptionsProcessor.getDescriptions(refEntity) != null) {
                  if (containingClass != ref.get()) {
                    problemDescriptionsProcessor.ignoreElement(refEntity);
                    return false;
                  }
                }
                else {
                  final UElement anchor = refEntity instanceof RefMethod
                                          ? ((RefMethod)refEntity).getUastElement().getUastAnchor()
                                          : ((RefField)refEntity).getUastElement().getUastAnchor();
                  if (anchor == null) return false;
                  final PsiElement identifier = anchor.getSourcePsi();
                  final ProblemDescriptor problemDescriptor = createProblemDescriptor(manager, identifier, containingClass);
                  problemDescriptionsProcessor.addProblemElement(refEntity, problemDescriptor);
                  ref.set(containingClass);
                }
                return true;
              }
            };
          if (refEntity instanceof RefMethod) {
            globalJavaContext.enqueueMethodUsagesProcessor((RefMethod)refEntity, processor);
          }
          else if (refEntity instanceof RefField) {
            globalJavaContext.enqueueFieldUsagesProcessor((RefField)refEntity, processor);
          }
        }
      }
    });

    return false;
  }

  static boolean isSingletonField(PsiField field) {
    return field.hasModifierProperty(PsiModifier.FINAL) &&
           field.hasModifierProperty(PsiModifier.STATIC) &&
           field.getContainingClass() == PsiUtil.resolveClassInClassTypeOnly(field.getType());
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

    AccessibleVisitor(PsiElement elementToCheck, PsiElement place) {
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
     * @return the class the specified member is used from, or null if it is
     *         used from 0 or more than 1 other classes.
     */
    @Nullable
    public PsiClass findUsageClass(PsiMember member) {
      ProgressManager.getInstance().runProcess(() -> {
        final Query<PsiReference> query = member instanceof PsiMethod
                                          ? MethodReferencesSearch.search((PsiMethod)member)
                                          : ReferencesSearch.search(member);
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

    StaticMethodOnlyUsedInOneClassLocalInspection(StaticMethodOnlyUsedInOneClassInspection settingsDelegate) {
      super(settingsDelegate);
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
      final PsiMember member = (PsiMember)infos[0];
      final PsiClass usageClass = (PsiClass)infos[1];
      return (usageClass instanceof PsiAnonymousClass)
             ? InspectionGadgetsBundle.message("static.method.only.used.in.one.anonymous.class.problem.descriptor",
                                               (member instanceof PsiMethod) ? 1 : 2,
                                               ((PsiAnonymousClass)usageClass).getBaseClassReference().getText())
             : InspectionGadgetsBundle.message("static.method.only.used.in.one.class.problem.descriptor",
                                               (member instanceof PsiMethod) ? 1 : 2,
                                               usageClass.getName());
    }

    @Override
    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
      final PsiMember member = (PsiMember)infos[0];
      final PsiClass usageClass = (PsiClass)infos[1];
      return new StaticMethodOnlyUsedInOneClassFix(usageClass, member instanceof PsiMethod);
    }

    private static class StaticMethodOnlyUsedInOneClassFix extends RefactoringInspectionGadgetsFix {

      private final SmartPsiElementPointer<PsiClass> myUsageClass;
      private final boolean myMethod;

      StaticMethodOnlyUsedInOneClassFix(PsiClass usageClass, boolean method) {
        myMethod = method;
        final SmartPointerManager pointerManager = SmartPointerManager.getInstance(usageClass.getProject());
        myUsageClass = pointerManager.createSmartPsiElementPointer(usageClass);
      }

      @Override
      @NotNull
      public String getFamilyName() {
        return InspectionGadgetsBundle.message("static.method.only.used.in.one.class.quickfix", myMethod ? 1 : 2);
      }

      @NotNull
      @Override
      public RefactoringActionHandler getHandler() {
        return RefactoringActionHandlerFactory.getInstance().createMoveHandler();
      }

      @NotNull
      @Override
      public DataContext enhanceDataContext(DataContext context) {
        PsiClass element = myUsageClass.getElement();
        return element == null ? context : SimpleDataContext.getSimpleContext(LangDataKeys.TARGET_PSI_ELEMENT, element, context);
      }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
      return new StaticMethodOnlyUsedInOneClassVisitor();
    }

    private class StaticMethodOnlyUsedInOneClassVisitor extends BaseInspectionVisitor {

      @Override
      public void visitField(PsiField field) {
        super.visitField(field);
        if (!field.hasModifierProperty(PsiModifier.STATIC) || field.hasModifierProperty(PsiModifier.PRIVATE)) return;
        if (field instanceof PsiEnumConstant || isSingletonField(field)) return;
        if (DeclarationSearchUtils.isTooExpensiveToSearch(field, true)) return;
        final PsiClass usageClass = getUsageClass(field);
        if (usageClass == null) return;
        registerFieldError(field, field, usageClass);
      }

      @Override
      public void visitMethod(final PsiMethod method) {
        super.visitMethod(method);
        if (!method.hasModifierProperty(PsiModifier.STATIC) ||
            method.hasModifierProperty(PsiModifier.PRIVATE) ||
            method.getNameIdentifier() == null) {
          return;
        }
        if (MethodUtils.isFactoryMethod(method) || MethodUtils.isConvenienceOverload(method)) {
          return;
        }
        if (DeclarationSearchUtils.isTooExpensiveToSearch(method, true)) return;
        final PsiClass usageClass = getUsageClass(method);
        if (usageClass == null) return;
        registerMethodError(method, method, usageClass);
      }

      private PsiClass getUsageClass(PsiMember member) {
        final UsageProcessor usageProcessor = new UsageProcessor();
        final PsiClass usageClass = usageProcessor.findUsageClass(member);
        if (usageClass == null) {
          return null;
        }
        final PsiClass containingClass = member.getContainingClass();
        if (containingClass == null || usageClass.equals(containingClass)) {
          return null;
        }
        if (mySettingsDelegate.ignoreTestClasses && TestUtils.isInTestCode(usageClass)) {
          return null;
        }
        if (usageClass.getContainingClass() != null && !usageClass.hasModifierProperty(PsiModifier.STATIC) ||
            PsiUtil.isLocalOrAnonymousClass(usageClass)) {
          if (mySettingsDelegate.ignoreAnonymousClasses || PsiTreeUtil.isAncestor(containingClass, usageClass, true)) {
            return null;
          }
        }
        if (mySettingsDelegate.ignoreUtilityClasses && UtilityClassUtil.isUtilityClass(containingClass)) {
          return null;
        }
        if (mySettingsDelegate.ignoreOnConflicts) {
          if (member instanceof PsiMethod) {
            if (usageClass.findMethodsBySignature((PsiMethod)member, true).length > 0 ||
                !areReferenceTargetsAccessible(member, usageClass)) {
              return null;
            }
          }
          else if (member instanceof PsiField) {
            if (usageClass.findFieldByName(member.getName(), true) != null || !areReferenceTargetsAccessible(member, usageClass)) {
              return null;
            }
          }
        }
        return usageClass;
      }
    }
  }
}
