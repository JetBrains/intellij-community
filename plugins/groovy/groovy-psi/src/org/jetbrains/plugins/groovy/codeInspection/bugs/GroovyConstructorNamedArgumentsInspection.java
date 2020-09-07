// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrNamedArgumentsOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyConstructorReference;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GroovyConstructorNamedArgumentsInspection extends BaseInspection {

  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new MyVisitor();
  }

  @Override
  protected String buildErrorString(Object... args) {
    assert args.length == 1 && args[0] instanceof String;
    return (String)args[0];
  }

  private static class MyVisitor extends BaseInspectionVisitor {

    @Override
    public void visitListOrMap(@NotNull GrListOrMap listOrMap) {
      super.visitListOrMap(listOrMap);
      GroovyConstructorReference reference = listOrMap.getConstructorReference();
      if (reference == null) return;
      processConstructor(listOrMap, reference.advancedResolve());
    }

    @Override
    public void visitNewExpression(@NotNull GrNewExpression newExpression) {
      super.visitNewExpression(newExpression);

      GrCodeReferenceElement refElement = newExpression.getReferenceElement();
      if (refElement == null) return;

      final GroovyResolveResult constructorResolveResult = newExpression.advancedResolve();
      GrNamedArgumentsOwner owner = getNamedArgumentsOwner(newExpression);
      if (owner == null) return;
      processConstructor(owner, constructorResolveResult);
    }

    public void processConstructor(@NotNull GrNamedArgumentsOwner owner, @NotNull GroovyResolveResult constructorResolveResult) {
      final PsiElement constructor = constructorResolveResult.getElement();
      if (constructor != null) {
        if (!PsiUtil.isConstructorHasRequiredParameters((PsiMethod)constructor)) {
          checkDefaultMapConstructor(owner, constructor);
        }
      }
    }

    private static @Nullable GrNamedArgumentsOwner getNamedArgumentsOwner(@NotNull GrNewExpression newExpression) {
      var argList = newExpression.getArgumentList();
      if (argList == null) return null;
      var expressionArguments = argList.getExpressionArguments();
      var namedArguments = argList.getNamedArguments();
      if (expressionArguments.length == 1 && namedArguments.length == 0 && expressionArguments[0] instanceof GrListOrMap) {
        return (GrNamedArgumentsOwner)expressionArguments[0];
      }
      else if (expressionArguments.length == 0) {
        return argList;
      }
      else {
        return null;
      }
    }

    private void checkDefaultMapConstructor(GrNamedArgumentsOwner owner, PsiElement element) {
      if (owner == null) return;

      final GrNamedArgument[] args = owner.getNamedArguments();
      for (GrNamedArgument arg : args) {
        final GrArgumentLabel label = arg.getLabel();
        if (label == null) continue;
        String labelName = label.getName();
        if (labelName == null) {
          final PsiElement nameElement = label.getNameElement();
          if (nameElement instanceof GrExpression) {
            final PsiType argType = ((GrExpression)nameElement).getType();
            if (argType != null &&
                !TypesUtil.isAssignableByMethodCallConversion(TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, arg), argType, arg)) {
              registerError(nameElement, GroovyBundle.message("property.name.expected"));
            }
          }
          else if (!"*".equals(nameElement.getText())) {
            registerError(nameElement, GroovyBundle.message("property.name.expected"));
          }
        }
        else {
          var propertyReference = label.getConstructorPropertyReference();
          final PsiElement resolved = propertyReference == null ? null : propertyReference.resolve();
          if (resolved == null) {

            if (element instanceof PsiMember && !(element instanceof PsiClass)) {
              element = ((PsiMember)element).getContainingClass();
            }

            List<LocalQuickFix> fixes = new ArrayList<>(2);
            if (element instanceof GrTypeDefinition) {
              fixes.add(GroovyQuickFixFactory.getInstance()
                          .createCreateFieldFromConstructorLabelFix((GrTypeDefinition)element, label.getNamedArgument()));
            }
            if (element instanceof PsiClass) {
              fixes.add(GroovyQuickFixFactory.getInstance().createDynamicPropertyFix(label, (PsiClass)element));
            }

            registerError(label, GroovyBundle.message("no.such.property", label.getName()), fixes.toArray(LocalQuickFix.EMPTY_ARRAY),
                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
          }
          else if (resolved instanceof PsiModifierListOwner) {
            if (((PsiModifierListOwner)resolved).hasModifierProperty(PsiModifier.FINAL)) {
              registerError(label, GroovyBundle.message("inspection.message.property.0.is.final", labelName), LocalQuickFix.EMPTY_ARRAY,
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
          }
        }
      }
    }
  }
}
