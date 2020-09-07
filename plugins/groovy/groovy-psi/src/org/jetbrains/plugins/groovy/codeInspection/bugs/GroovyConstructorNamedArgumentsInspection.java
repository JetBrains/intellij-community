// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.util.SmartList;
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.LazyThreadSafetyMode;
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
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyConstructorReference;
import org.jetbrains.plugins.groovy.lang.resolve.ast.AffectedMembersCache;
import org.jetbrains.plugins.groovy.lang.resolve.ast.GrGeneratedConstructorUtils;
import org.jetbrains.plugins.groovy.lang.resolve.ast.TupleConstructorAttributes;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
          return;
        }
        PsiClass containingClass = ((PsiMethod)constructor).getContainingClass();
        if (containingClass != null) {
          PsiAnnotation annotation = containingClass.getAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_MAP_CONSTRUCTOR);
          if (annotation != null) {
            checkGeneratedMapConstructor(owner, containingClass, annotation);
          }
        }
      }
    }

    private void checkGeneratedMapConstructor(@NotNull GrNamedArgumentsOwner owner,
                                              @NotNull PsiClass containingClass,
                                              @NotNull PsiAnnotation annotation) {
      Lazy<Set<String>> affectedMembers = LazyKt.lazy(LazyThreadSafetyMode.NONE, () ->
        GrGeneratedConstructorUtils.getAffectedMembersCache(annotation)
        .getAffectedMembers().stream()
        .map(AffectedMembersCache::getExternalName)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet()));
      for (GrNamedArgument argument : owner.getNamedArguments()) {
        GrArgumentLabel label = argument.getLabel();
        if (label == null) continue;
        var propertyReference = label.getConstructorPropertyReference();
        final PsiElement resolved = propertyReference == null ? null : propertyReference.resolve();
        if (resolved != null) {
          String name = label.getName();
          if (name != null && !affectedMembers.getValue().contains(name)) {
            var fix = generateMapConstructorFix(resolved, containingClass, annotation);
            registerError(label, GroovyBundle.message("inspection.message.property.0.is.ignored.by.map.constructor", name), fix,
                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
          }
        }
        else {
          registerAbsentIdentifierError(containingClass, label);
        }
      }
    }

    private static LocalQuickFix @NotNull [] generateMapConstructorFix(@NotNull PsiElement resolvedElement,
                                                                       @NotNull PsiClass containingClass,
                                                                       @NotNull PsiAnnotation annotation) {
      if (annotation instanceof LightElement) {
        return LocalQuickFix.EMPTY_ARRAY;
      }
      if (!(containingClass instanceof GrTypeDefinition)) {
        return LocalQuickFix.EMPTY_ARRAY;
      }
      if (!(resolvedElement instanceof PsiMember)) {
        return LocalQuickFix.EMPTY_ARRAY;
      }
      if (((PsiMember)resolvedElement).getContainingClass() != containingClass && resolvedElement instanceof PsiMethod) {
        return LocalQuickFix.EMPTY_ARRAY;
      }
      if (annotation.hasAttribute(TupleConstructorAttributes.INCLUDES) || annotation.hasAttribute(TupleConstructorAttributes.EXCLUDES)) {
        return LocalQuickFix.EMPTY_ARRAY;
      }
      return new LocalQuickFix[]{GroovyQuickFixFactory.getInstance().createMapConstructorFix()};
    }

    private void registerAbsentIdentifierError(@NotNull PsiClass clazz,
                                               @NotNull GrArgumentLabel label) {
      List<LocalQuickFix> fixes = new SmartList<>();
      if (clazz instanceof GrTypeDefinition) {
        fixes.add(GroovyQuickFixFactory.getInstance()
                    .createCreateFieldFromConstructorLabelFix((GrTypeDefinition)clazz, label.getNamedArgument()));
      }
      fixes.add(GroovyQuickFixFactory.getInstance().createDynamicPropertyFix(label, clazz));

      registerError(label, GroovyBundle.message("no.such.property", label.getName()), fixes.toArray(LocalQuickFix.EMPTY_ARRAY),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
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
            if (element instanceof PsiClass) {
              registerAbsentIdentifierError((PsiClass)element, label);
            }
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
