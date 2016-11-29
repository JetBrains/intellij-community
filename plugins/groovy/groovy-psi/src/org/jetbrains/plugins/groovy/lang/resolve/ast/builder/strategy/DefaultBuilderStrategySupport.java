/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve.ast.builder.strategy;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightPsiClassBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ast.builder.BuilderAnnotationContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ast.builder.BuilderHelperLightPsiClass;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createType;

public class DefaultBuilderStrategySupport extends BuilderAnnotationContributor {

  public static final String DEFAULT_STRATEGY_NAME = "DefaultStrategy";

  @Override
  public void applyTransformation(@NotNull TransformationContext context) {
    new DefaultBuilderStrategyHandler(context).doProcess();
  }

  private static class DefaultBuilderStrategyHandler {

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
      final PsiClass builderClass = createBuilderClass(builderAnno, myContext.getCodeClass().getCodeFields());
      myContext.addMethod(createBuilderMethod(builderClass, builderAnno));
      myContext.addInnerClass(builderClass);
    }

    @NotNull
    private LightPsiClassBuilder createBuilderClass(@NotNull final PsiAnnotation annotation, @NotNull GrVariable[] setters) {
      return createBuilderClass(annotation, setters, null);
    }

    @NotNull
    private LightPsiClassBuilder createBuilderClass(@NotNull final PsiAnnotation annotation,
                                                    @NotNull GrVariable[] setters,
                                                    @Nullable PsiType builtType) {
      final LightPsiClassBuilder builderClass = new BuilderHelperLightPsiClass(
        myContainingClass, getBuilderClassName(annotation, myContainingClass)
      );

      for (GrVariable field : setters) {
        builderClass.addMethod(createFieldSetter(builderClass, field, annotation));
      }

      final LightMethodBuilder buildMethod = createBuildMethod(
        annotation, builtType == null ? createType(myContainingClass) : builtType
      );
      return builderClass.addMethod(buildMethod);
    }

    @NotNull
    private LightMethodBuilder createBuilderMethod(@NotNull PsiClass builderClass, @NotNull PsiAnnotation annotation) {
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

    @NotNull
    private static String getBuilderMethodName(@NotNull PsiAnnotation annotation) {
      final String builderMethodName = AnnotationUtil.getDeclaredStringAttributeValue(annotation, "builderMethodName");
      return StringUtil.isEmpty(builderMethodName) ? "builder" : builderMethodName;
    }
  }

  @NotNull
  public static String getBuilderClassName(@NotNull PsiAnnotation annotation, @NotNull GrTypeDefinition clazz) {
    final String builderClassName = AnnotationUtil.getDeclaredStringAttributeValue(annotation, "builderClassName");
    return builderClassName == null ? String.format("%s%s", clazz.getName(), "Builder") : builderClassName;
  }

  @NotNull
  public static LightMethodBuilder createBuildMethod(@NotNull PsiAnnotation annotation, @NotNull PsiType builtType) {
    final LightMethodBuilder buildMethod = new LightMethodBuilder(annotation.getManager(), getBuildMethodName(annotation));
    buildMethod.setOriginInfo(ORIGIN_INFO);
    buildMethod.setMethodReturnType(builtType);
    return buildMethod;
  }

  @NotNull
  public static LightMethodBuilder createFieldSetter(@NotNull PsiClass builderClass,
                                                     @NotNull GrVariable field,
                                                     @NotNull PsiAnnotation annotation) {
    return createFieldSetter(builderClass, field.getName(), field.getType(), annotation, field);
  }

  @NotNull
  public static LightMethodBuilder createFieldSetter(@NotNull PsiClass builderClass,
                                                     @NotNull String name,
                                                     @NotNull PsiType type,
                                                     @NotNull PsiAnnotation annotation,
                                                     @NotNull PsiElement navigationElement) {
    final LightMethodBuilder fieldSetter = new LightMethodBuilder(builderClass.getManager(), getFieldMethodName(annotation, name));
    fieldSetter.addModifier(PsiModifier.PUBLIC);
    fieldSetter.addParameter(name, type);
    fieldSetter.setContainingClass(builderClass);
    fieldSetter.setMethodReturnType(JavaPsiFacade.getElementFactory(builderClass.getProject()).createType(builderClass));
    fieldSetter.setNavigationElement(navigationElement);
    fieldSetter.setOriginInfo(ORIGIN_INFO);
    return fieldSetter;
  }

  @NotNull
  public static String getFieldMethodName(@NotNull PsiAnnotation annotation, @NotNull String fieldName) {
    final String prefix = AnnotationUtil.getDeclaredStringAttributeValue(annotation, "prefix");
    return StringUtil.isEmpty(prefix) ? fieldName : String.format("%s%s", prefix, StringUtil.capitalize(fieldName));
  }

  @NotNull
  private static String getBuildMethodName(@NotNull PsiAnnotation annotation) {
    final String buildMethodName = AnnotationUtil.getDeclaredStringAttributeValue(annotation, "buildMethodName");
    return StringUtil.isEmpty(buildMethodName) ? "build" : buildMethodName;
  }
}
