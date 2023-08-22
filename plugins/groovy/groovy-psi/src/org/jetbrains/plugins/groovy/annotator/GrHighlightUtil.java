// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Max Medvedev
 */
public final class GrHighlightUtil {
  private static final Logger LOG = Logger.getInstance(GrHighlightUtil.class);

  private static Set<GrVariable> getReassignedVariables(final PsiElement scope) {
    return CachedValuesManager.getCachedValue(scope, () -> CachedValueProvider.Result.create(collectReassignedVariables(scope), scope));
  }

  private static Set<GrVariable> collectReassignedVariables(PsiElement scope) {
    final Set<GrVariable> result = new HashSet<>();
    PsiTreeUtil.processElements(scope, new PsiElementProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element) {
        if (!(element instanceof GrReferenceExpression ref) || ((GrReferenceExpression)element).isQualified()) {
          return true;
        }

        if (isWriteAccess(ref)) {
          PsiElement target = ref.resolve();
          if (target instanceof GrVariable && ((GrVariable)target).getInitializerGroovy() != null ||
              target instanceof GrParameter) {
            result.add((GrVariable)target);
          }
        }
        return true;
      }
    });
    return result;
  }

  private static boolean isWriteAccess(GrReferenceExpression element) {
    return PsiUtil.isLValue(element) ||
           element.getParent() instanceof GrUnaryExpression && ((GrUnaryExpression)element.getParent()).isPostfix();
  }

  public static boolean isReassigned(final GrVariable var) {
    LOG.assertTrue(!DumbService.getInstance(var.getProject()).isDumb());

    PsiMethod method = PsiTreeUtil.getParentOfType(var, PsiMethod.class);
    PsiNamedElement scope = method == null ? var.getContainingFile() : method;
    return scope != null && getReassignedVariables(scope).contains(var);
  }

  public static boolean isDeclarationAssignment(GrReferenceExpression refExpr) {
    return isAssignmentLhs(refExpr) && isScriptPropertyAccess(refExpr);
  }

  private static boolean isAssignmentLhs(GrReferenceExpression refExpr) {
    return refExpr.getParent() instanceof GrAssignmentExpression &&
           refExpr.equals(((GrAssignmentExpression)refExpr.getParent()).getLValue());
  }

  private static boolean isScriptPropertyAccess(GrReferenceExpression refExpr) {
    final GrExpression qualifier = refExpr.getQualifierExpression();
    if (qualifier == null) {
      final PsiClass clazz = PsiTreeUtil.getParentOfType(refExpr, PsiClass.class);
      if (clazz == null) { //script
        return true;
      }
      return false; //in class, a property should normally be defined, so it's not a declaration
    }

    final PsiType type = qualifier.getType();
    if (type instanceof PsiClassType classType &&
        !(qualifier instanceof GrReferenceExpression && ((GrReferenceExpression)qualifier).resolve() instanceof GroovyScriptClass)) {
      final PsiClass psiClass = classType.resolve();
      if (psiClass instanceof GroovyScriptClass) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public static TextRange getMethodHeaderTextRange(@NotNull PsiMethod method) {
    int startOffset = method.getTextRange().getStartOffset();
    int endOffset = method.getParameterList().getTextRange().getEndOffset();
    return new TextRange(startOffset, endOffset);
  }

  public static TextRange getClassHeaderTextRange(GrTypeDefinition clazz) {
    final GrModifierList modifierList = clazz.getModifierList();
    final int startOffset = modifierList != null ? modifierList.getTextOffset() : clazz.getTextOffset();
    final GrImplementsClause implementsClause = clazz.getImplementsClause();

    final int endOffset;
    if (implementsClause != null) {
      endOffset = implementsClause.getTextRange().getEndOffset();
    }
    else {
      final GrExtendsClause extendsClause = clazz.getExtendsClause();
      if (extendsClause != null) {
        endOffset = extendsClause.getTextRange().getEndOffset();
      }
      else {
        endOffset = clazz.getNameIdentifierGroovy().getTextRange().getEndOffset();
      }
    }
    return new TextRange(startOffset, endOffset);
  }

  public static TextRange getInitializerHeaderTextRange(GrClassInitializer initializer) {
    final PsiModifierList modifierList = initializer.getModifierList();
    final GrOpenBlock block = initializer.getBlock();

    final TextRange textRange = modifierList.getTextRange();
    LOG.assertTrue(textRange != null, initializer.getClass() + ":" + initializer.getText());
    int startOffset = textRange.getStartOffset();
    int endOffset = block.getLBrace().getTextRange().getEndOffset() + 1;

    return new TextRange(startOffset, endOffset);
  }

  @Nullable
  public static GrMember findClassMemberContainer(@NotNull GrReferenceExpression ref, @NotNull PsiClass aClass) {
    for (PsiElement parent = ref.getParent(); parent != null && parent != aClass; parent = parent.getParent()) {
      if (parent instanceof GrMember && ((GrMember)parent).getContainingClass() == aClass) {
        return (GrMember)parent;
      }
    }
    return null;
  }
}
