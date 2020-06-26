// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.ast.builder.strategy;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightPsiClassBuilder;
import com.intellij.psi.impl.light.LightTypeParameterBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ast.builder.BuilderAnnotationContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ast.builder.BuilderHelperLightPsiClass;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

import java.util.Arrays;
import java.util.Objects;

import static org.jetbrains.plugins.groovy.lang.resolve.ast.builder.strategy.DefaultBuilderStrategySupport.getBuilderClassName;
import static org.jetbrains.plugins.groovy.lang.resolve.ast.builder.strategy.DefaultBuilderStrategySupport.getFieldMethodName;

public class InitializerBuilderStrategySupport extends BuilderAnnotationContributor {

  public static final String INITIALIZER_STRATEGY_NAME = "InitializerStrategy";
  public static final String SET_FQN = "groovy.transform.builder.InitializerStrategy.SET";
  public static final String UNSET_FQN = "groovy.transform.builder.InitializerStrategy.UNSET";

  @Override
  public void applyTransformation(@NotNull TransformationContext context) {
    new InitializerBuilderStrategyHandler(context).doProcess();
  }

  private static final class InitializerBuilderStrategyHandler {

    private final @NotNull TransformationContext myContext;
    private final @NotNull GrTypeDefinition myContainingClass;
    private final @NotNull PsiElementFactory myElementFactory;

    private InitializerBuilderStrategyHandler(@NotNull TransformationContext context) {
      myContext = context;
      myContainingClass = context.getCodeClass();
      myElementFactory = PsiElementFactory.getInstance(myContainingClass.getProject());
    }

    public void doProcess() {
      processTypeDefinition();
      processConstructors();
    }

    private void processTypeDefinition() {
      final PsiAnnotation builderAnno = PsiImplUtil.getAnnotation(myContainingClass, BUILDER_FQN);
      if (!isApplicable(builderAnno, INITIALIZER_STRATEGY_NAME)) return;
      boolean includeSuper = isIncludeSuperProperties(builderAnno);
      final PsiClass builderClass = createBuilderClass(builderAnno, getFields(myContext, includeSuper));
      myContext.addMethod(createBuilderMethod(builderClass, builderAnno));
      myContext.addMethod(createBuilderConstructor(myContainingClass, builderClass, builderAnno));
      myContext.addInnerClass(builderClass);
    }

    @NotNull
    private LightPsiClassBuilder createBuilderClass(@NotNull final PsiAnnotation annotation,
                                                    PsiVariable @NotNull [] setters) {
      final LightPsiClassBuilder builderClass = new BuilderHelperLightPsiClass(
        myContainingClass, getBuilderClassName(annotation, myContainingClass)
      );
      for (int i = 0; i < setters.length; i++) {
        builderClass.getTypeParameterList().addParameter(new InitializerTypeParameter(builderClass, i));
      }
      for (int i = 0; i < setters.length; i++) {
        builderClass.addMethod(createFieldSetter(builderClass, setters[i], annotation, i));
      }
      return builderClass.addMethod(createBuildMethod(annotation, builderClass));
    }

    @NotNull
    private LightMethodBuilder createFieldSetter(@NotNull LightPsiClassBuilder builderClass,
                                                 @NotNull PsiVariable field,
                                                 @NotNull PsiAnnotation annotation,
                                                 int currentField) {
      final String name = Objects.requireNonNull(field.getName());
      final LightMethodBuilder fieldSetter = new LightMethodBuilder(builderClass.getManager(), getFieldMethodName(annotation, name));
      final PsiSubstitutor substitutor = PsiSubstitutor.EMPTY.put(
        builderClass.getTypeParameters()[currentField],
        myElementFactory.createTypeByFQClassName(SET_FQN, annotation.getResolveScope())
      );
      fieldSetter.addModifier(PsiModifier.PUBLIC);
      fieldSetter.addParameter(name, field.getType());
      fieldSetter.setContainingClass(builderClass);
      fieldSetter.setMethodReturnType(myElementFactory.createType(builderClass, substitutor));
      fieldSetter.setNavigationElement(field);
      fieldSetter.setOriginInfo(ORIGIN_INFO);
      return fieldSetter;
    }

    private LightMethodBuilder createBuildMethod(@NotNull PsiAnnotation annotation, @NotNull PsiClass builderClass) {
      LightMethodBuilder buildMethod = new LightMethodBuilder(annotation.getManager(), builderClass.getLanguage(), getBuildMethodName(annotation));
      buildMethod.addModifier(PsiModifier.STATIC);
      buildMethod.setContainingClass(builderClass);
      buildMethod.setOriginInfo(ORIGIN_INFO);
      buildMethod.setNavigationElement(annotation);
      buildMethod.setMethodReturnType(createAllSetUnsetType(builderClass, false));
      return buildMethod;
    }

    @NotNull
    private LightMethodBuilder createBuilderMethod(@NotNull PsiClass builderClass, @NotNull PsiAnnotation annotation) {
      final LightMethodBuilder builderMethod = new LightMethodBuilder(myContext.getManager(), getBuilderMethodName(annotation));
      builderMethod.addModifier(PsiModifier.STATIC);
      builderMethod.setOriginInfo(ORIGIN_INFO);
      builderMethod.setNavigationElement(annotation);
      builderMethod.setMethodReturnType(createAllSetUnsetType(builderClass, false));
      return builderMethod;
    }

    @NotNull
    private LightMethodBuilder createBuilderConstructor(@NotNull PsiClass constructedClass,
                                                        @NotNull PsiClass builderClass,
                                                        @NotNull PsiAnnotation annotation) {
      final LightMethodBuilder constructor = new LightMethodBuilder(constructedClass, constructedClass.getLanguage()).addParameter(
        "builder", createAllSetUnsetType(builderClass, true)
      ).setConstructor(true);
      constructor.setNavigationElement(annotation);
      constructor.setOriginInfo(ORIGIN_INFO);
      return constructor;
    }

    private void processConstructors() {
      for (GrMethod method : myContainingClass.getCodeMethods()) {
        final PsiAnnotation annotation = PsiImplUtil.getAnnotation(method, BUILDER_FQN);
        if (!isApplicable(annotation, INITIALIZER_STRATEGY_NAME)) return;
        if (method.isConstructor()) {
          processConstructor(method, annotation);
        }
      }
    }

    private void processConstructor(@NotNull GrMethod method, @NotNull PsiAnnotation annotation) {
      PsiClass builderClass = createBuilderClass(annotation, method.getParameters());
      myContext.addMethod(createBuilderMethod(builderClass, annotation));
      myContext.addMethod(createBuilderConstructor(myContainingClass, builderClass, annotation));
      myContext.addInnerClass(builderClass);
    }

    @NotNull
    private static String getBuilderMethodName(@NotNull PsiAnnotation annotation) {
      final String builderMethodName = AnnotationUtil.getDeclaredStringAttributeValue(annotation, "builderMethodName");
      return StringUtil.isEmpty(builderMethodName) ? "createInitializer" : builderMethodName;
    }

    @NotNull
    private static String getBuildMethodName(@NotNull PsiAnnotation annotation) {
      final String builderMethodName = AnnotationUtil.getDeclaredStringAttributeValue(annotation, "buildMethodName");
      return StringUtil.isEmpty(builderMethodName) ? "create" : builderMethodName;
    }

    @NotNull
    private PsiType createAllSetUnsetType(@NotNull PsiClass builderClass, boolean setUnset) {
      final PsiClassType type = myElementFactory.createTypeByFQClassName(
        setUnset ? SET_FQN : UNSET_FQN,
        builderClass.getResolveScope()
      );
      final PsiType[] mappings = PsiType.createArray(builderClass.getTypeParameters().length);
      Arrays.fill(mappings, type);
      return myElementFactory.createType(builderClass, mappings);
    }
  }

  private static class InitializerTypeParameter extends LightTypeParameterBuilder {

    InitializerTypeParameter(PsiTypeParameterListOwner owner, int index) {
      super("T" + index, owner, index);
    }
  }
}
