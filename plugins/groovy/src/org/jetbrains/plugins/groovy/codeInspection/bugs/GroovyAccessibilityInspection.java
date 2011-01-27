/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConstructorCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GroovyAccessibilityInspection extends BaseInspection {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyAccessibilityInspection");

  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new MyVisitor();
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return PROBABLE_BUGS;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return GroovyInspectionBundle.message("access.to.inaccessible.element");
  }

  @Override
  protected String buildErrorString(Object... args) {
    return GroovyBundle.message("cannot.access", args);
  }

  @Override
  protected GroovyFix[] buildFixes(PsiElement location) {
    if (!(location instanceof GrReferenceElement || location instanceof GrConstructorCall)) {
      location = location.getParent();
    }

    final GroovyResolveResult resolveResult;
    if (location instanceof GrConstructorCall) {
      resolveResult = ((GrConstructorCall)location).advancedResolve();
    }
    else {
      resolveResult = ((GrReferenceElement)location).advancedResolve();
    }

    final PsiElement element = resolveResult.getElement();
    if (!(element instanceof PsiMember)) return GroovyFix.EMPTY_ARRAY;
    final PsiMember refElement = (PsiMember)element;

    if (refElement instanceof PsiCompiledElement) return GroovyFix.EMPTY_ARRAY;

    PsiModifierList modifierList = refElement.getModifierList();
    if (modifierList == null) return GroovyFix.EMPTY_ARRAY;

    List<GroovyFix> fixes = new ArrayList<GroovyFix>();
    try {
      Project project = refElement.getProject();
      JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      PsiModifierList modifierListCopy = facade.getElementFactory().createFieldFromText("int a;", null).getModifierList();
      modifierListCopy.setModifierProperty(PsiModifier.STATIC, modifierList.hasModifierProperty(PsiModifier.STATIC));
      @Modifier String minModifier = PsiModifier.PROTECTED;
      if (refElement.hasModifierProperty(PsiModifier.PROTECTED)) {
        minModifier = PsiModifier.PUBLIC;
      }
      String[] modifiers = {PsiModifier.PROTECTED, PsiModifier.PUBLIC, PsiModifier.PACKAGE_LOCAL};
      PsiClass accessObjectClass = PsiTreeUtil.getParentOfType(location, PsiClass.class, false);
      if (accessObjectClass == null) {
        accessObjectClass = ((GroovyFile)location.getContainingFile()).getScriptClass();
      }
      for (int i = ArrayUtil.indexOf(modifiers, minModifier); i < modifiers.length; i++) {
        String modifier = modifiers[i];
        modifierListCopy.setModifierProperty(modifier, true);
        if (facade.getResolveHelper().isAccessible(refElement, modifierListCopy, location, accessObjectClass, null)) {
          fixes.add(new GrModifierFix(refElement, refElement.getModifierList(), modifier, true));
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return fixes.toArray(new GroovyFix[fixes.size()]);
  }

  private static class GrModifierFix extends GroovyFix {
    private PsiMember myMember;
    private PsiModifierList myModifierList;
    private String myModifier;
    private boolean myDoSet;

    public GrModifierFix(@NotNull PsiMember member,
                       @NotNull PsiModifierList modifierList,
                       String modifier,
                       boolean doSet) {
      myMember = member;
      myModifierList = modifierList;
      myModifier = modifier;
      myDoSet = doSet;
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      myModifierList.setModifierProperty(myModifier, myDoSet);
    }

    @NotNull
    @Override
    public String getName() {
      String name;
      final PsiClass containingClass = myMember.getContainingClass();
      String containingClassName;
      if (containingClass != null) {
        containingClassName = containingClass.getName() + ".";
      }
      else {
        containingClassName = "";
      }

      name = containingClassName + myMember.getName();

      String modifierText = toPresentableText(myModifier);

      if (myDoSet) {
        return GroovyBundle.message("change.modifier", name, modifierText);
      }
      else {
        return GroovyBundle.message("change.modifier.not", name, modifierText);
      }
    }
  }

  private static String toPresentableText(String modifier) {
    return GroovyBundle.message(modifier + ".visibility.presentation");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  private static class MyVisitor extends BaseInspectionVisitor {
    @Override
    public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
      super.visitCodeReferenceElement(refElement);
      checkRef(refElement);
    }

    @Override
    public void visitReferenceExpression(GrReferenceExpression ref) {
      super.visitReferenceExpression(ref);
      checkRef(ref);
    }

    @Override
    public void visitNewExpression(GrNewExpression newExpression) {
      checkConstructorCall(newExpression);
    }

    private void checkConstructorCall(GrConstructorCall call) {
      final GroovyResolveResult result = call.advancedResolve();
      if (result.getElement() == null) return;
      final PsiElement constructor = result.getElement();
      if (!(constructor instanceof PsiMethod)) return;
      if (!result.isAccessible()) {

        PsiElement refElement = null;
        if (call instanceof GrNewExpression) {
          refElement = ((GrNewExpression)call).getReferenceElement();
        }
        else if (call instanceof GrConstructorInvocation) {
          refElement = ((GrConstructorInvocation)call).getThisOrSuperKeyword();
        }
        if (refElement == null) {
          refElement = call;
        }


        registerError(refElement,
                      PsiFormatUtil.formatMethod((PsiMethod)constructor, PsiSubstitutor.EMPTY,
                                                 PsiFormatUtil.SHOW_NAME |
                                                 PsiFormatUtil.SHOW_TYPE |
                                                 PsiFormatUtil.TYPE_AFTER |
                                                 PsiFormatUtil.SHOW_PARAMETERS,
                                                 PsiFormatUtil.SHOW_TYPE
                      ));
      }
    }

    @Override
    public void visitConstructorInvocation(GrConstructorInvocation invocation) {
      super.visitConstructorInvocation(invocation);
      checkConstructorCall(invocation);
    }

    private void checkRef(GrReferenceElement ref) {
      final GroovyResolveResult result = ref.advancedResolve();
      if (result == null) return;
      if (result.getElement() == null) return;
      if (!result.isAccessible()) {
        registerError(getErrorLocation(ref), ref.getReferenceName());
      }
    }

    @NotNull
    private static PsiElement getErrorLocation(GrReferenceElement ref) {
      final PsiElement nameElement = ref.getReferenceNameElement();
      if (nameElement != null) return nameElement;
      return ref;
    }
  }
}
