// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

/**
 * @author Maxim.Medvedev
 */
public final class GrUnresolvableLocalCollisionDetector {
  private GrUnresolvableLocalCollisionDetector() {
  }

  public static void visitLocalsCollisions(PsiElement element,
                                           final String newName,
                                           GroovyPsiElement place,
                                           final CollidingVariableVisitor visitor) {
    visitDownstreamCollisions(newName, place, visitor);
    visitUpstreamCollisions(element, newName, place, visitor);
  }

  private static void visitUpstreamCollisions(PsiElement element,
                                              String newName,
                                              GroovyPsiElement place,
                                              CollidingVariableVisitor visitor) {
    final GrReferenceExpression refExpr =
      GroovyPsiElementFactory.getInstance(place.getProject()).createReferenceExpressionFromText(newName, place);
    final GroovyResolveResult[] results = refExpr.multiResolve(false);
    for (GroovyResolveResult result : results) {
      final PsiElement resolved = result.getElement();
      if (resolved instanceof GrParameter || (resolved instanceof GrVariable && !(resolved instanceof GrField))) {
        final PsiElement parent = PsiTreeUtil.findCommonParent(resolved, element);
        if (parent != null) {
          PsiElement current = element;
          while (current != null && current != parent) {
            if (current instanceof PsiMethod || current instanceof PsiClass || current instanceof GrClosableBlock) {
              return;
            }
            current = current.getParent();
          }
        }

        if (!place.getManager().areElementsEquivalent(element, resolved)) {
          visitor.visitCollidingVariable((PsiVariable)resolved);
        }
      }
    }
  }

  private static void visitDownstreamCollisions(final String newName, GroovyPsiElement place, final CollidingVariableVisitor visitor) {
    place.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitVariableDeclaration(@NotNull GrVariableDeclaration variableDeclaration) {
        final GrVariable[] variables = variableDeclaration.getVariables();
        for (GrVariable variable : variables) {
          if (variable.getName().equals(newName)) {
            visitor.visitCollidingVariable(variable);
          }
        }
      }

      @Override
      public void visitMethod(@NotNull GrMethod method) {}

      @Override
      public void visitClosure(@NotNull GrClosableBlock closure) {}

      @Override
      public void visitTypeDefinition(@NotNull GrTypeDefinition typeDefinition) {}
    });
  }

  interface CollidingVariableVisitor {
    void visitCollidingVariable(PsiVariable variable);
  }
}
