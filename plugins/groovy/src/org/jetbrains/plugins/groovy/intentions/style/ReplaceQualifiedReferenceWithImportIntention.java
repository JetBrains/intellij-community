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
package org.jetbrains.plugins.groovy.intentions.style;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

/**
 * @author Max Medvedev
 */
public class ReplaceQualifiedReferenceWithImportIntention extends Intention {
  private static final Logger LOG = Logger.getInstance(ReplaceQualifiedReferenceWithImportIntention.class);

  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    LOG.assertTrue(element instanceof GrReferenceElement, element.getClass().getCanonicalName() + " : " + element.getText());
    GrReferenceAdjuster.shortenReference((GrQualifiedReference)element);
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new MyPredicate();
  }

  public static boolean canBeReplacedWithImport(PsiElement element) {
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
    if (resolveResult.getElement() == null || !resolveResult.isAccessible() || !resolveResult.isStaticsOK()) {
      return true;
    }
    if (element.getManager().areElementsEquivalent(resolved, resolveResult.getElement())) {
      return true;
    }

    return false;
  }

  private static class MyPredicate implements PsiElementPredicate {
    @Override
    public boolean satisfiedBy(PsiElement element) {
      return canBeReplacedWithImport(element);
    }
  }
}

