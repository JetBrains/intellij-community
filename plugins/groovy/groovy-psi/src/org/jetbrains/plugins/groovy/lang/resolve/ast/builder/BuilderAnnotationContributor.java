// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.ast.builder;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

import java.util.Collection;

import static java.util.Arrays.asList;

public abstract class BuilderAnnotationContributor implements AstTransformationSupport {

  public static final @NlsSafe String BUILDER_PACKAGE = "groovy.transform.builder";
  public static final @NlsSafe String BUILDER_FQN = BUILDER_PACKAGE + ".Builder";
  public static final @NonNls String ORIGIN_INFO = "via @Builder";
  public static final @NlsSafe String STRATEGY_ATTRIBUTE = "builderStrategy";

  @Contract("null, _ -> false")
  public static boolean isApplicable(@Nullable PsiAnnotation annotation, @NotNull String strategy) {
    if (annotation == null) return false;
    PsiClass aClass = GrAnnotationUtil.inferClassAttribute(annotation, STRATEGY_ATTRIBUTE);
    if (aClass == null) return false;
    return StringUtil.getQualifiedName(BUILDER_PACKAGE, strategy).equals(aClass.getQualifiedName());
  }

  public static PsiField[] getFields(@NotNull TransformationContext context, boolean includeSuper) {
    return filterFields(includeSuper ? context.getAllFields(false) : context.getFields(), context);
  }

  public static PsiField[] getFields(@NotNull GrTypeDefinition clazz,
                                     boolean includeSuper,
                                     @NotNull TransformationContext context) {
    return filterFields(includeSuper ? asList(GrClassImplUtil.getAllFields(clazz, false)) : asList(clazz.getFields()), context);
  }

  private static PsiField[] filterFields(Collection<? extends PsiField> collectedFields,
                                         @NotNull TransformationContext context) {
    return collectedFields.stream()
      .filter(field -> {
        PsiModifierList modifierList = field.getModifierList();
        if (modifierList instanceof GrModifierList) {
          return !context.hasModifierProperty((GrModifierList)modifierList, PsiModifier.STATIC);
        } else {
          return true;
        }
      })
      .filter(field -> {
        PsiClass aClass = field.getContainingClass();
        if (aClass == null || aClass.getQualifiedName() == null) {
          return false;
        }
        String name = aClass.getQualifiedName();
        return !name.equals(GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT) && !name.equals(GroovyCommonClassNames.GROOVY_OBJECT);
      })
      .toArray(PsiField[]::new);
  }

  public static boolean isIncludeSuperProperties(@NotNull PsiAnnotation annotation) {
    return PsiUtil.getAnnoAttributeValue(annotation, "includeSuperProperties", false);
  }
}
