// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

/**
 * Utility class to solve name conflicts related to local variable names of method to be inlined
 */
public final class InlineMethodConflictSolver {
  private InlineMethodConflictSolver() {}

  @NotNull
  public static String suggestNewName(@NotNull String startName, @Nullable GrMethod method, @NotNull PsiElement call, String... otherNames) {
    String newName;
    int i = 1;
    PsiElement parent = call.getParent();
    while (!(parent instanceof GrVariableDeclarationOwner) && parent != null) {
      parent = parent.getParent();
    }
    if (parent == null || isValidName(startName, parent, call) && isValid(startName, otherNames)) {
      return startName;
    }
    do {
      newName = startName + i;
      i++;
    } while (!((method == null || isValidNameInMethod(newName, method)) &&
        isValidName(newName, parent, call) && isValid(newName, otherNames)));
    return newName;
  }

  static boolean isValid(@Nullable String name, String ... otherNames) {
    for (String otherName : otherNames) {
      if (otherName.equals(name)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isValidNameInMethod(@NotNull String name, @NotNull GrMethod method) {
    for (GrParameter parameter : method.getParameters()) {
      if (name.equals(parameter.getName())) return false;
    }
    final GrOpenBlock block = method.getBlock();
    if (block != null) {
      return isValidNameDown(name, block, null);
    }
    return true;
  }

  public static boolean isValidName(@NotNull String name, @NotNull PsiElement scopeElement, PsiElement call) {
    if (isValidNameDown(name, scopeElement, call)) {
      if (!(scopeElement instanceof GroovyFileBase)) {
        return isValidNameUp(name, scopeElement, call);
      } else {
        return true;
      }
    } else {
      return false;
    }
  }

  private static boolean isValidNameDown(@NotNull String name, @NotNull PsiElement startElement, @Nullable PsiElement call) {

    PsiElement child = startElement.getFirstChild();
    while (child != null) {
      // Do not check defined classes, methods, closures and blocks before
      if (child instanceof GrTypeDefinition ||
          child instanceof GrMethod ||
          call != null && GroovyRefactoringUtil.isAppropriateContainerForIntroduceVariable(child) &&
              child.getTextRange().getEndOffset() < call.getTextRange().getStartOffset()) {
        child = child.getNextSibling();
        continue;
      }
      if (child instanceof GrAssignmentExpression) {
        GrExpression lValue = ((GrAssignmentExpression) child).getLValue();
        if (lValue instanceof GrReferenceExpression expr) {
          if (expr.getQualifierExpression() == null && name.equals(expr.getReferenceName())) {
            return false;
          }
        }
      }

      if (child instanceof GrVariable && name.equals(((GrVariable) child).getName())) {
          return false;
      } else {
        boolean inner = isValidNameDown(name, child, call);
        if (!inner) return false;
      }
      child = child.getNextSibling();
    }
    return true;
  }

  private static boolean isValidNameUp(@NotNull String name, @NotNull PsiElement startElement, @Nullable PsiElement call) {
    if (startElement instanceof PsiFile) {
      return true;
    }

    PsiElement prevSibling = startElement.getPrevSibling();
    while (prevSibling != null) {
      if (!isValidNameDown(name, prevSibling, call)) {
        return false;
      }
      prevSibling = prevSibling.getPrevSibling();
    }

    PsiElement parent = startElement.getParent();
    return parent == null || parent instanceof PsiDirectory || isValidNameUp(name, parent, call);
  }




}
