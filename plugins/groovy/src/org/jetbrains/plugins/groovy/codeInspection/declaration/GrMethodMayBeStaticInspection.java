/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.declaration;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrThisSuperReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;

import javax.swing.*;

/**
 * @author Max Medvedev
 */
public class GrMethodMayBeStaticInspection extends BaseInspection {
  public boolean myOnlyPrivateOrFinal = false;
  public boolean myIgnoreEmptyMethods = true;

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(GroovyInspectionBundle.message("method.may.be.static.only.private.or.final.option"), "myOnlyPrivateOrFinal");
    optionsPanel.addCheckbox(GroovyInspectionBundle.message("method.may.be.static.ignore.empty.method.option"), "myIgnoreEmptyMethods");
    return optionsPanel;
  }


  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitMethod(GrMethod method) {
        Result result = checkMethod(method);
        LocalQuickFix[] fixes;
        switch (result) {
          case mayBeStatic:
            fixes = new LocalQuickFix[]{new GrModifierLocalFix(method, PsiModifier.STATIC, false, true)};
            registerError(method.getNameIdentifierGroovy(), GroovyInspectionBundle.message("method.may.be.static"), fixes,
                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            break;
          case mayBeStaticButHaveInstanceRefsInClosure:
            break;
          case mayNotBeStatic:
            break;
        }
      }
    };
  }

  enum Result {
    mayBeStatic,
    mayBeStaticButHaveInstanceRefsInClosure,
    mayNotBeStatic

  }

  private Result checkMethod(final GrMethod method) {
    if (method.hasModifierProperty(PsiModifier.STATIC)) return Result.mayNotBeStatic;
    if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) return Result.mayNotBeStatic;
    if (method.isConstructor()) return Result.mayNotBeStatic;
    if (method.getContainingClass() instanceof GroovyScriptClass) return Result.mayNotBeStatic;
    if (SuperMethodsSearch.search(method, null, true, false).findFirst() != null) return Result.mayNotBeStatic;
    if (OverridingMethodsSearch.search(method).findFirst() != null) return Result.mayNotBeStatic;
    if (ignoreMethod(method))return Result.mayNotBeStatic;

    if (myOnlyPrivateOrFinal) {
      if (!(method.hasModifierProperty(PsiModifier.FINAL) || method.hasModifierProperty(PsiModifier.PRIVATE))) return Result.mayNotBeStatic;
    }

    GrOpenBlock block = method.getBlock();
    if (block == null) return Result.mayNotBeStatic;
    if (myIgnoreEmptyMethods && block.getStatements().length == 0) return Result.mayNotBeStatic;

    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return Result.mayNotBeStatic;
    if (containingClass.getContainingClass() != null && !containingClass.hasModifierProperty(PsiModifier.STATIC)) {
      return Result.mayNotBeStatic;
    }

    final ExtensionsArea rootArea = Extensions.getRootArea();
    final ExtensionPoint<Condition<PsiElement>> extensionPoint = rootArea.getExtensionPoint("com.intellij.cantBeStatic");
    final Condition<PsiElement>[] addins = extensionPoint.getExtensions();
    for (Condition<PsiElement> addin : addins) {
      if (addin.value(method)) {
        return Result.mayNotBeStatic;
      }
    }


    MethodMayBeStaticVisitor visitor = new MethodMayBeStaticVisitor();
    method.accept(visitor);

    boolean haveInstanceRefsInClosure = visitor.haveInstanceRefsInClosure();
    boolean haveInstanceRefs = visitor.haveInstanceRefsOutsideClosures();

    if (!haveInstanceRefsInClosure && !haveInstanceRefs) {
      return Result.mayBeStatic;
    }
    else if (haveInstanceRefsInClosure && !haveInstanceRefs) {
      return Result.mayBeStaticButHaveInstanceRefsInClosure;
    }
    else {
      return Result.mayNotBeStatic;
    }
  }

  private static boolean ignoreMethod(GrMethod method) {
    final GrParameter[] parameters = method.getParameters();
    if (method.getName().equals("propertyMissing") && (parameters.length == 2 || parameters.length == 1)) return true;
    if (method.getName().equals("methodMissing") && (parameters.length == 2 || parameters.length == 1)) return true;

    return false;
  }

  private static boolean isPrintOrPrintln(PsiElement element) {
    return element instanceof GrGdkMethod && element instanceof PsiMethod &&
           ("print".equals(((PsiMethod)element).getName()) || "println".equals(((PsiMethod)element).getName()));
  }

  private static class MethodMayBeStaticVisitor extends GroovyRecursiveElementVisitor {
    private boolean myHaveNoInstanceRefs = true;
    private boolean myHaveNoInstanceRefsInClosure = true;

    private int myIsInClosure = 0;

    @Override
    public void visitElement(GroovyPsiElement element) {
      if (!myHaveNoInstanceRefs) return;
      super.visitElement(element);
    }

    @Override
    public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
      GrExpression qualifier = referenceExpression.getQualifierExpression();
      if (qualifier == null || qualifier instanceof GrThisSuperReferenceExpression) {
        GroovyResolveResult result = referenceExpression.advancedResolve();
        PsiElement element = result.getElement();
        if (isPrintOrPrintln(element)) return; //print & println are resolved in all places

        GroovyPsiElement resolveContext = result.getCurrentFileResolveContext();
        if (qualifier == null && resolveContext != null) return;
        if (element instanceof PsiClass && ((PsiClass)element).getContainingClass() == null) return;
        if (element instanceof PsiMember && !((PsiMember)element).hasModifierProperty(PsiModifier.STATIC)) {
          registerInstanceRefs();
        }
      }
      else {
        super.visitReferenceExpression(referenceExpression);
      }
    }

    @Override
    public void visitThisSuperReferenceExpression(GrThisSuperReferenceExpression expression) {
      if (expression.getParent() instanceof GrReferenceExpression) return;

      registerInstanceRefs();
    }

    private void registerInstanceRefs() {
      if (myIsInClosure > 0) {
        myHaveNoInstanceRefsInClosure = false;
      }
      else {
        myHaveNoInstanceRefs = false;
      }
    }

    @Override
    public void visitClosure(GrClosableBlock closure) {
      myIsInClosure++;
      super.visitClosure(closure);
      myIsInClosure--;
    }

    @Override
    public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
      super.visitCodeReferenceElement(refElement);

      if (!myHaveNoInstanceRefs) return;

      final PsiElement resolvedElement = refElement.resolve();
      if (!(resolvedElement instanceof PsiClass)) return;

      final PsiClass aClass = (PsiClass)resolvedElement;
      final PsiElement scope = aClass.getScope();

      if (!(scope instanceof PsiClass)) return;
      if (!aClass.hasModifierProperty(PsiModifier.STATIC)) {
        registerInstanceRefs();
      }
    }

    public boolean haveInstanceRefsOutsideClosures() {
      return !myHaveNoInstanceRefs;
    }

    public boolean haveInstanceRefsInClosure() {
      return !myHaveNoInstanceRefsInClosure;
    }
  }
}
