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
import com.intellij.psi.impl.light.LightTypeParameterBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ast.Members;
import org.jetbrains.plugins.groovy.lang.resolve.ast.builder.BuilderAnnotationContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ast.builder.BuilderHelperLightPsiClass;

import java.util.Collection;

import static org.jetbrains.plugins.groovy.lang.resolve.ast.builder.strategy.DefaultBuilderStrategySupport.getBuilderClassName;
import static org.jetbrains.plugins.groovy.lang.resolve.ast.builder.strategy.DefaultBuilderStrategySupport.getFieldMethodName;

public class InitializerBuilderStrategySupport extends BuilderAnnotationContributor {

  public static final String INITIALIZER_STRATEGY_NAME = "InitializerStrategy";
  public static final String SET_FQN = "groovy.transform.builder.InitializerStrategy.SET";
  public static final String UNSET_FQN = "groovy.transform.builder.InitializerStrategy.UNSET";

  @Override
  public void collectClasses(@NotNull GrTypeDefinition clazz, Collection<PsiClass> collector) {
    collector.addAll(collect(clazz).getClasses());
  }

  @Override
  public void collectMethods(@NotNull GrTypeDefinition clazz, Collection<PsiMethod> collector) {
    collector.addAll(collect(clazz).getMethods());
  }

  @NotNull
  public Members collect(@NotNull GrTypeDefinition clazz) {
    return new InitializerBuilderStrategyHandler(clazz).doProcess();
  }

  private static class InitializerBuilderStrategyHandler {

    private final @NotNull GrTypeDefinition myContainingClass;
    private final @NotNull PsiElementFactory myElementFactory;
    private final Members myMembers;

    private InitializerBuilderStrategyHandler(@NotNull GrTypeDefinition typeDefinition) {
      myContainingClass = typeDefinition;
      myElementFactory = PsiElementFactory.SERVICE.getInstance(typeDefinition.getProject());
      myMembers = Members.create();
    }

    @NotNull
    private PsiManager getManager() {
      return myContainingClass.getManager();
    }

    @NotNull
    public Members doProcess() {
      processTypeDefinition();
      processConstructors();
      return myMembers;
    }

    private void processTypeDefinition() {
      final PsiAnnotation builderAnno = PsiImplUtil.getAnnotation(myContainingClass, BUILDER_FQN);
      if (!isApplicable(builderAnno, INITIALIZER_STRATEGY_NAME)) return;

      final PsiClass builderClass = createBuilderClass(builderAnno, myContainingClass.getCodeFields());
      myMembers.getMethods().add(createBuilderMethod(builderClass, builderAnno));
      myMembers.getMethods().add(createBuilderConstructor(myContainingClass, builderClass, builderAnno));
      myMembers.getClasses().add(builderClass);
    }

    @NotNull
    private LightPsiClassBuilder createBuilderClass(@NotNull final PsiAnnotation annotation,
                                                    @NotNull GrVariable[] setters) {
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
                                                 @NotNull GrVariable field,
                                                 @NotNull PsiAnnotation annotation,
                                                 int currentField) {
      final String name = field.getName();
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
      final LightMethodBuilder builderMethod = new LightMethodBuilder(getManager(), getBuilderMethodName(annotation));
      builderMethod.addModifier(PsiModifier.STATIC);
      builderMethod.setContainingClass(myContainingClass);
      builderMethod.setOriginInfo(ORIGIN_INFO);
      builderMethod.setNavigationElement(annotation);
      builderMethod.setMethodReturnType(createAllSetUnsetType(builderClass, false));
      return builderMethod;
    }

    @NotNull
    private LightMethodBuilder createBuilderConstructor(@NotNull PsiClass constructedClass, @NotNull PsiClass builderClass, PsiAnnotation annotation) {
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

    private void processConstructor(@NotNull GrMethod method, PsiAnnotation annotation) {
      PsiClass builderClass = createBuilderClass(annotation, method.getParameters());
      myMembers.getMethods().add(createBuilderMethod(builderClass, annotation));
      myMembers.getMethods().add(createBuilderConstructor(myContainingClass, builderClass, annotation));
      myMembers.getClasses().add(builderClass);
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
      for (int i = 0; i < mappings.length; i++) {
        mappings[i] = type;
      }
      return myElementFactory.createType(builderClass, mappings);
    }
  }

  private static class InitializerTypeParameter extends LightTypeParameterBuilder {

    public InitializerTypeParameter(PsiTypeParameterListOwner owner, int index) {
      super("T" + index, owner, index);
    }
  }
}
