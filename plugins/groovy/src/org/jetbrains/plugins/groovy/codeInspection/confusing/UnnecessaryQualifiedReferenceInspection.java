/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeStyle.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

/**
 * @author Max Medvedev
 */
public class UnnecessaryQualifiedReferenceInspection extends BaseInspection {
  private static final Logger LOG = Logger.getInstance(UnnecessaryQualifiedReferenceInspection.class);

  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
        super.visitCodeReferenceElement(refElement);

        if (canBeSimplified(refElement)) {
          registerError(refElement);
        }
      }

      @Override
      public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
        super.visitReferenceExpression(referenceExpression);

        if (canBeSimplified(referenceExpression) || isQualifiedStaticMethodWithUnnecessaryQualifier(referenceExpression)) {
          registerError(referenceExpression);
        }
      }
    };
  }

  private static boolean isQualifiedStaticMethodWithUnnecessaryQualifier(GrReferenceExpression ref) {
    if (ref.getQualifier() == null) return false;

    final PsiElement resolved = ref.resolve();
    if (!(resolved instanceof PsiMember)) return false;
    if (!((PsiMember)resolved).hasModifierProperty(PsiModifier.STATIC)) return false;

    PsiElement copyResolved;
    final PsiElement parent = ref.getParent();
    if (parent instanceof GrMethodCall) {
      final GrMethodCall copy = (GrMethodCall)parent.copy();
      GrReferenceExpression invoked = (GrReferenceExpression)copy.getInvokedExpression();
      assert invoked != null;

      invoked.setQualifier(null);

      copyResolved = ((GrReferenceExpression)copy.getInvokedExpression()).resolve();
    }
    else {
      final GrReferenceExpression copy = (GrReferenceExpression)ref.copy();
      copy.setQualifier(null);
      copyResolved = copy.resolve();
    }
    return ref.getManager().areElementsEquivalent(copyResolved, resolved);
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return CONFUSING_CODE_CONSTRUCTS;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return GroovyInspectionBundle.message("unnecessary.qualified.reference");
  }

  @Override
  protected String buildErrorString(Object... args) {
    return GroovyInspectionBundle.message("unnecessary.qualified.reference");
  }

  @Override
  protected GroovyFix buildFix(@NotNull PsiElement location) {
    return new GroovyFix() {
      @Override
      protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
        final PsiElement startElement = descriptor.getStartElement();
        LOG.assertTrue(startElement instanceof GrReferenceElement<?>);
        GrReferenceAdjuster.shortenReference((GrQualifiedReference<?>)startElement);
      }

      @NotNull
      @Override
      public String getName() {
        return GroovyInspectionBundle.message("replace.qualified.name.with.import");
      }
    };
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  private static boolean canBeSimplified(PsiElement element) {
    if (PsiTreeUtil.getParentOfType(element, PsiComment.class) != null) return false;

    if (element instanceof GrCodeReferenceElement) {
      if (PsiTreeUtil.getParentOfType(element, GrImportStatement.class, GrPackageDefinition.class) != null) return false;
    }
    else if (element instanceof GrReferenceExpression) {
      if (!GrReferenceAdjuster.seemsToBeQualifiedClassName((GrReferenceExpression)element)) return false;
    }
    else {
      return false;
    }

    final GrReferenceElement ref = (GrReferenceElement)element;
    if (ref.getQualifier() == null) return false;
    if (!(ref.getContainingFile() instanceof GroovyFileBase)) return false;

    final PsiElement resolved = ref.resolve();
    if (!(resolved instanceof PsiClass)) return false;

    final String name = ((PsiClass)resolved).getName();
    if (name == null) return false;

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(element.getProject());
    final GrReferenceExpression shortedRef = factory.createReferenceExpressionFromText(name, element);
    final GroovyResolveResult resolveResult = shortedRef.advancedResolve();

    if (element.getManager().areElementsEquivalent(resolved, resolveResult.getElement())) {
      return true;
    }

    final PsiClass containingClass = ((PsiClass)resolved).getContainingClass();
    if (containingClass != null &&
        !CodeStyleSettingsManager.getSettings(resolved.getProject()).getCustomSettings(GroovyCodeStyleSettings.class).INSERT_INNER_CLASS_IMPORTS) {
      return false;
    }

    if (resolveResult.getElement() == null || !resolveResult.isAccessible() || !resolveResult.isStaticsOK()) {
      return true;
    }

    return false;
  }
}
