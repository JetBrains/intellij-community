/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.lang.resolve.ast.builder.BuilderHelperLightPsiClass;
import org.jetbrains.plugins.groovy.lang.resolve.ast.builder.GrBuilderStrategySupport;

public class DefaultBuilderStrategySupport extends GrBuilderStrategySupport {

  public static final String DEFAULT_STRATEGY_FQN = "groovy.transform.builder.DefaultStrategy";

  @NotNull
  public Members process(@NotNull final GrTypeDefinition typeDefinition) {
    return new DefaultBuilderStrategyHandler(typeDefinition).doProcess();
  }

  private static class DefaultBuilderStrategyHandler {

    private final @NotNull GrTypeDefinition myContainingClass;
    private final @NotNull PsiElementFactory myElementFactory;
    private final Members myMembers;

    private DefaultBuilderStrategyHandler(@NotNull GrTypeDefinition typeDefinition) {
      myContainingClass = typeDefinition;
      myElementFactory = PsiElementFactory.SERVICE.getInstance(typeDefinition.getProject());
      myMembers = new Members();
    }

    @NotNull
    private PsiManager getManager() {
      return myContainingClass.getManager();
    }

    @NotNull
    public Members doProcess() {
      processTypeDefinition();
      processMethods();
      return myMembers;
    }

    private void processTypeDefinition() {
      final PsiAnnotation builderAnno = PsiImplUtil.getAnnotation(myContainingClass, BUILDER_FQN);
      if (builderAnno == null || !DEFAULT_STRATEGY_FQN.equals(getStrategy(myContainingClass))) return;
      final PsiClass builderClass = createBuilderClass(builderAnno, myContainingClass.getCodeFields());
      final LightMethodBuilder builderMethod = createBuilderMethod(builderClass, builderAnno);
      myMembers.classes.add(builderClass);
      myMembers.methods.add(builderMethod);
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

      final LightMethodBuilder buildMethod = new LightMethodBuilder(getManager(), getBuildMethodName(annotation));
      buildMethod.setContainingClass(builderClass);
      buildMethod.setOriginInfo(ORIGIN_INFO);
      buildMethod.setMethodReturnType(builtType == null ? myElementFactory.createType(myContainingClass) : builtType);
      return builderClass.addMethod(buildMethod);
    }

    @NotNull
    private LightMethodBuilder createBuilderMethod(@NotNull PsiClass builderClass, @NotNull PsiAnnotation annotation) {
      final LightMethodBuilder builderMethod = new LightMethodBuilder(getManager(), getBuilderMethodName(annotation));
      builderMethod.addModifier(PsiModifier.STATIC);
      builderMethod.setContainingClass(myContainingClass);
      builderMethod.setOriginInfo(ORIGIN_INFO);
      builderMethod.setNavigationElement(annotation);
      builderMethod.setMethodReturnType(myElementFactory.createType(builderClass));
      return builderMethod;
    }

    private void processMethods() {
      for (GrMethod method : myContainingClass.getCodeMethods()) {
        processMethod(method);
      }
    }

    private void processMethod(@NotNull GrMethod method) {
      final PsiAnnotation annotation = PsiImplUtil.getAnnotation(method, BUILDER_FQN);
      if (annotation == null || !DEFAULT_STRATEGY_FQN.equals(getStrategy(method))) return;
      if (method.isConstructor()) {
        processConstructor(method, annotation);
      }
      else if (method.hasModifierProperty(PsiModifier.STATIC)) {
        processFactoryMethod(method, annotation);
      }
    }

    private void processConstructor(@NotNull GrMethod method, PsiAnnotation annotation) {
      PsiClass builderClass = createBuilderClass(annotation, method.getParameters());
      LightMethodBuilder builderMethod = createBuilderMethod(builderClass, annotation);
      myMembers.methods.add(builderMethod);
      myMembers.classes.add(builderClass);
    }

    private void processFactoryMethod(@NotNull GrMethod method, PsiAnnotation annotation) {
      PsiClass builderClass = createBuilderClass(annotation, method.getParameters(), method.getReturnType());
      LightMethodBuilder builderMethod = createBuilderMethod(builderClass, annotation);
      myMembers.methods.add(builderMethod);
      myMembers.classes.add(builderClass);
    }

    @NotNull
    private LightMethodBuilder createFieldSetter(@NotNull PsiClass builderClass, @NotNull GrVariable field, @NotNull PsiAnnotation annotation) {
      final String name = field.getName();
      final LightMethodBuilder fieldSetter = new LightMethodBuilder(getManager(), getFieldMethodName(annotation, name));
      fieldSetter.addModifier(PsiModifier.PUBLIC);
      fieldSetter.addParameter(name, field.getType(), false);
      fieldSetter.setContainingClass(builderClass);
      fieldSetter.setMethodReturnType(myElementFactory.createType(builderClass));
      fieldSetter.setNavigationElement(field);
      return fieldSetter;
    }

    @NotNull
    private static String getBuilderMethodName(@NotNull PsiAnnotation annotation) {
      final String builderMethodName = AnnotationUtil.getDeclaredStringAttributeValue(annotation, "builderMethodName");
      return builderMethodName == null ? "builder" : builderMethodName;
    }

    @NotNull
    private static String getBuilderClassName(@NotNull PsiAnnotation annotation, @NotNull GrTypeDefinition clazz) {
      final String builderClassName = AnnotationUtil.getDeclaredStringAttributeValue(annotation, "builderClassName");
      return builderClassName == null ? String.format("%s%s", clazz.getName(), "Builder") : builderClassName;
    }

    @NotNull
    private static String getFieldMethodName(@NotNull PsiAnnotation annotation, @NotNull String fieldName) {
      final String prefix = AnnotationUtil.getDeclaredStringAttributeValue(annotation, "prefix");
      return prefix == null ? fieldName : String.format("%s%s", prefix, StringUtil.capitalize(fieldName));
    }

    @NotNull
    private static String getBuildMethodName(@NotNull PsiAnnotation annotation) {
      final String buildMethodName = AnnotationUtil.getDeclaredStringAttributeValue(annotation, "buildMethodName");
      return buildMethodName == null ? "build" : buildMethodName;
    }
  }
}
