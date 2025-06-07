// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.ast.builder.strategy;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightPsiClassBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ast.builder.BuilderAnnotationContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ast.builder.BuilderHelperLightPsiClass;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

import java.util.Objects;

import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createType;

public final class DefaultBuilderStrategySupport extends BuilderAnnotationContributor {

  public static final String DEFAULT_STRATEGY_NAME = "DefaultStrategy";

  @Override
  public void applyTransformation(@NotNull TransformationContext context) {
    new DefaultBuilderStrategyHandler(context).doProcess();
  }

  private static final class DefaultBuilderStrategyHandler {

    private final @NotNull TransformationContext myContext;
    private final @NotNull GrTypeDefinition myContainingClass;

    private DefaultBuilderStrategyHandler(@NotNull TransformationContext context) {
      myContext = context;
      myContainingClass = context.getCodeClass();
    }

    public void doProcess() {
      processTypeDefinition();
      processMethods();
    }

    private void processTypeDefinition() {
      final PsiAnnotation builderAnno = PsiImplUtil.getAnnotation(myContainingClass, BUILDER_FQN);
      if (!isApplicable(builderAnno, DEFAULT_STRATEGY_NAME)) return;
      boolean includeSuper = isIncludeSuperProperties(builderAnno);
      final PsiClass builderClass = createBuilderClass(builderAnno, getFields(myContext, includeSuper));
      myContext.addMethod(createBuilderMethod(builderClass, builderAnno));
      myContext.addInnerClass(builderClass);
    }

    private @NotNull LightPsiClassBuilder createBuilderClass(final @NotNull PsiAnnotation annotation, PsiVariable @NotNull [] setters) {
      return createBuilderClass(annotation, setters, null);
    }

    private @NotNull LightPsiClassBuilder createBuilderClass(final @NotNull PsiAnnotation annotation,
                                                             PsiVariable @NotNull [] setters,
                                                             @Nullable PsiType builtType) {
      final LightPsiClassBuilder builderClass = new BuilderHelperLightPsiClass(
        myContainingClass, getBuilderClassName(annotation, myContainingClass)
      );

      for (PsiVariable field : setters) {
        LightMethodBuilder setter = createFieldSetter(builderClass, field, annotation, myContext);
        builderClass.addMethod(setter);
      }

      final LightMethodBuilder buildMethod = createBuildMethod(
        annotation, builtType == null ? createType(myContainingClass) : builtType
      );
      return builderClass.addMethod(buildMethod);
    }

    private @NotNull LightMethodBuilder createBuilderMethod(@NotNull PsiClass builderClass, @NotNull PsiAnnotation annotation) {
      final LightMethodBuilder builderMethod = new LightMethodBuilder(myContext.getManager(), getBuilderMethodName(annotation));
      builderMethod.addModifier(PsiModifier.STATIC);
      builderMethod.setOriginInfo(ORIGIN_INFO);
      builderMethod.setNavigationElement(annotation);
      builderMethod.setMethodReturnType(createType(builderClass));
      return builderMethod;
    }

    private void processMethods() {
      for (GrMethod method : myContext.getCodeClass().getCodeMethods()) {
        processMethod(method);
      }
    }

    private void processMethod(@NotNull GrMethod method) {
      final PsiAnnotation annotation = PsiImplUtil.getAnnotation(method, BUILDER_FQN);
      if (!isApplicable(annotation, DEFAULT_STRATEGY_NAME)) return;
      if (method.isConstructor()) {
        processConstructor(method, annotation);
      }
      else if (method.hasModifierProperty(PsiModifier.STATIC)) {
        processFactoryMethod(method, annotation);
      }
    }

    private void processConstructor(@NotNull GrMethod method, PsiAnnotation annotation) {
      PsiClass builderClass = createBuilderClass(annotation, method.getParameters());
      myContext.addMethod(createBuilderMethod(builderClass, annotation));
      myContext.addInnerClass(builderClass);
    }

    private void processFactoryMethod(@NotNull GrMethod method, PsiAnnotation annotation) {
      PsiClass builderClass = createBuilderClass(annotation, method.getParameters(), method.getReturnType());
      myContext.addMethod(createBuilderMethod(builderClass, annotation));
      myContext.addInnerClass(builderClass);
    }

    private static @NlsSafe @NotNull String getBuilderMethodName(@NotNull PsiAnnotation annotation) {
      final String builderMethodName = AnnotationUtil.getDeclaredStringAttributeValue(annotation, "builderMethodName");
      return StringUtil.isEmpty(builderMethodName) ? "builder" : builderMethodName;
    }
  }

  public static @NlsSafe @NotNull String getBuilderClassName(@NotNull PsiAnnotation annotation, @NotNull GrTypeDefinition clazz) {
    final String builderClassName = AnnotationUtil.getDeclaredStringAttributeValue(annotation, "builderClassName");
    return builderClassName == null ? clazz.getName() + "Builder" : builderClassName;
  }

  public static @NotNull LightMethodBuilder createBuildMethod(@NotNull PsiAnnotation annotation, @NotNull PsiType builtType) {
    final LightMethodBuilder buildMethod = new LightMethodBuilder(annotation.getManager(), getBuildMethodName(annotation));
    buildMethod.setOriginInfo(ORIGIN_INFO);
    buildMethod.setMethodReturnType(builtType);
    return buildMethod;
  }

  public static @NotNull LightMethodBuilder createFieldSetter(@NotNull PsiClass builderClass,
                                                              @NotNull PsiVariable field,
                                                              @NotNull PsiAnnotation annotation,
                                                              @NotNull TransformationContext context) {
    String name = Objects.requireNonNull(field.getName());
    return createFieldSetter(builderClass, name, field.getType(), annotation, field, context);
  }

  public static @NotNull LightMethodBuilder createFieldSetter(@NotNull PsiClass builderClass,
                                                              @NotNull String name,
                                                              @NotNull PsiType type,
                                                              @NotNull PsiAnnotation annotation,
                                                              @NotNull PsiElement navigationElement,
                                                              @NotNull TransformationContext context) {
    final LightMethodBuilder fieldSetter = new LightMethodBuilder(builderClass.getManager(), getFieldMethodName(annotation, name));
    fieldSetter.addModifier(PsiModifier.PUBLIC);
    fieldSetter.addParameter(name, type);
    fieldSetter.setContainingClass(builderClass);
    fieldSetter.setMethodReturnType(context.eraseClassType(JavaPsiFacade.getElementFactory(builderClass.getProject()).createType(builderClass, PsiSubstitutor.EMPTY)));
    fieldSetter.setNavigationElement(navigationElement);
    fieldSetter.setOriginInfo(ORIGIN_INFO);
    return fieldSetter;
  }

  public static @NotNull String getFieldMethodName(@NotNull PsiAnnotation annotation, @NotNull String fieldName) {
    final String prefix = AnnotationUtil.getDeclaredStringAttributeValue(annotation, "prefix");
    return StringUtil.isEmpty(prefix) ? fieldName : String.format("%s%s", prefix, StringUtil.capitalize(fieldName));
  }

  private static @NotNull String getBuildMethodName(@NotNull PsiAnnotation annotation) {
    final String buildMethodName = AnnotationUtil.getDeclaredStringAttributeValue(annotation, "buildMethodName");
    return StringUtil.isEmpty(buildMethodName) ? "build" : buildMethodName;
  }
}
