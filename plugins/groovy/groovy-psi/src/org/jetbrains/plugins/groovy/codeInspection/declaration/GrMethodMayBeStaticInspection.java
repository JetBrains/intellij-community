// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.declaration;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrModifierFix;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.swing.*;

import static org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle.message;

public class GrMethodMayBeStaticInspection extends BaseInspection {
  public boolean myIgnoreTraitMethods = true;
  public boolean myOnlyPrivateOrFinal = false;
  public boolean myIgnoreEmptyMethods = true;

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(message("method.may.be.static.option.ignore.trait.methods"), "myIgnoreTraitMethods");
    optionsPanel.addCheckbox(message("method.may.be.static.only.private.or.final.option"), "myOnlyPrivateOrFinal");
    optionsPanel.addCheckbox(message("method.may.be.static.ignore.empty.method.option"), "myIgnoreEmptyMethods");
    return optionsPanel;
  }

  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitMethod(@NotNull GrMethod method) {
        if (checkMethod(method)) {
          final GrModifierFix modifierFix = new GrModifierFix(method, PsiModifier.STATIC, false, true, descriptor -> {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            assert parent instanceof GrMethod : "element: " + element + ", parent:" + parent;
            return ((GrMethod)parent).getModifierList();
          });
          registerError(method.getNameIdentifierGroovy(), message("method.may.be.static"), new LocalQuickFix[]{modifierFix}, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }
      }
    };
  }

  private boolean checkMethod(final GrMethod method) {
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) return false;
    if (method.getModifierList().hasExplicitModifier(PsiModifier.ABSTRACT)) return false;
    if (method.isConstructor()) return false;

    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return false;

    if (myIgnoreTraitMethods && GrTraitUtil.isTrait(containingClass)) return false;
    if (SuperMethodsSearch.search(method, null, true, false).findFirst() != null) return false;
    if (OverridingMethodsSearch.search(method).findFirst() != null) return false;
    if (ignoreMethod(method)) return false;

    if (myOnlyPrivateOrFinal) {
      if (!(method.hasModifierProperty(PsiModifier.FINAL) || method.hasModifierProperty(PsiModifier.PRIVATE))) return false;
    }

    GrOpenBlock block = method.getBlock();
    if (block == null) return false;
    if (myIgnoreEmptyMethods && block.getStatements().length == 0) return false;

    if (containingClass.getContainingClass() != null && !containingClass.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }

    final Condition<PsiElement>[] addins = InspectionManager.CANT_BE_STATIC_EXTENSION.getExtensions();
    for (Condition<PsiElement> addin : addins) {
      if (addin.value(method)) {
        return false;
      }
    }


    MethodMayBeStaticVisitor visitor = new MethodMayBeStaticVisitor();
    method.accept(visitor);
    return !visitor.haveInstanceRefsOutsideClosures();
  }

  private static boolean ignoreMethod(GrMethod method) {
    final GrParameter[] parameters = method.getParameters();
    if (method.getName().equals("propertyMissing") && (parameters.length == 2 || parameters.length == 1)) return true;
    if (method.getName().equals("methodMissing") && (parameters.length == 2 || parameters.length == 1)) return true;
    if (method.getContainingClass() instanceof PsiAnonymousClass) return true;

    for (GrMethodMayBeStaticInspectionFilter filter : GrMethodMayBeStaticInspectionFilter.EP_NAME.getExtensions()) {
      if (filter.isIgnored(method)) {
        return true;
      }
    }

    return false;
  }

  private static boolean isPrintOrPrintln(PsiElement element) {
    return element instanceof GrGdkMethod &&
           ("print".equals(((PsiMethod)element).getName()) || "println".equals(((PsiMethod)element).getName()));
  }

  private static class MethodMayBeStaticVisitor extends GroovyRecursiveElementVisitor {
    private boolean myHaveInstanceRefs = false;

    @Override
    public void visitElement(@NotNull GroovyPsiElement element) {
      if (myHaveInstanceRefs) return;

      super.visitElement(element);
    }

    @Override
    public void visitReferenceExpression(@NotNull GrReferenceExpression referenceExpression) {
      if (myHaveInstanceRefs) return;

      if (PsiUtil.isSuperReference(referenceExpression)) {
        registerInstanceRef();
        return;
      }
      if (PsiUtil.isThisReference(referenceExpression)) {
        if (!(referenceExpression.getParent() instanceof GrReferenceExpression)) {//if we have reference parent -- check it instead of this ref
          registerInstanceRef();
        }

        return;
      }

      GrExpression qualifier = referenceExpression.getQualifierExpression();
      if (qualifier == null || PsiUtil.isThisOrSuperRef(qualifier)) {
        GroovyResolveResult result = referenceExpression.advancedResolve();
        PsiElement element = result.getElement();
        if (isPrintOrPrintln(element)) return;

        PsiElement resolveContext = result.getCurrentFileResolveContext();
        if (qualifier == null && resolveContext != null) return;
        if (element instanceof PsiClass && ((PsiClass)element).getContainingClass() == null) return;
        if (element instanceof PsiMember && !((PsiMember)element).hasModifierProperty(PsiModifier.STATIC)) {
          registerInstanceRef();
        }
        if (element == null) {
          registerInstanceRef();
        }
      }
      else {
        super.visitReferenceExpression(referenceExpression);
      }
    }

    private void registerInstanceRef() {
      myHaveInstanceRefs = true;
    }

    @Override
    public void visitCodeReferenceElement(@NotNull GrCodeReferenceElement refElement) {
      super.visitCodeReferenceElement(refElement);

      if (myHaveInstanceRefs) return;

      final PsiElement resolvedElement = refElement.resolve();
      if (!(resolvedElement instanceof PsiClass)) return;

      final PsiClass aClass = (PsiClass)resolvedElement;
      final PsiElement scope = aClass.getScope();

      if (!(scope instanceof PsiClass)) return;
      if (!aClass.hasModifierProperty(PsiModifier.STATIC)) {
        registerInstanceRef();
      }
    }

    public boolean haveInstanceRefsOutsideClosures() {
      return myHaveInstanceRefs;
    }
  }
}
