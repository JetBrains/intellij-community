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
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.light.LightMethodBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ast.Members;
import org.jetbrains.plugins.groovy.lang.resolve.ast.builder.BuilderAnnotationContributor;

import java.util.Collection;

public class SimpleBuilderStrategySupport extends BuilderAnnotationContributor {

  public static final String SIMPLE_STRATEGY_NAME = "SimpleStrategy";

  @Override
  public void collectMethods(@NotNull GrTypeDefinition clazz, Collection<PsiMethod> collector) {
    collector.addAll(collect(clazz).getMethods());
  }

  @NotNull
  public Members collect(@NotNull GrTypeDefinition typeDefinition) {
    final PsiAnnotation annotation = PsiImplUtil.getAnnotation(typeDefinition, BUILDER_FQN);
    if (!isApplicable(annotation, SIMPLE_STRATEGY_NAME)) return Members.EMPTY;
    final Members result = Members.create();
    for (GrField field : typeDefinition.getCodeFields()) {
      result.getMethods().add(createFieldSetter(typeDefinition, field, annotation));
    }
    return result;
  }

  @NotNull
  public static LightMethodBuilder createFieldSetter(@NotNull PsiClass builderClass, @NotNull GrVariable field, @NotNull PsiAnnotation annotation) {
    final String name = field.getName();
    final LightMethodBuilder fieldSetter = new LightMethodBuilder(builderClass.getManager(), getFieldMethodName(annotation, name));
    fieldSetter.addModifier(PsiModifier.PUBLIC);
    fieldSetter.addParameter(name, field.getType(), false);
    fieldSetter.setContainingClass(builderClass);
    fieldSetter.setMethodReturnType(createType(builderClass));
    fieldSetter.setNavigationElement(field);
    fieldSetter.setOriginInfo(ORIGIN_INFO);
    return fieldSetter;
  }

  @NotNull
  public static String getFieldMethodName(@NotNull PsiAnnotation annotation, @NotNull String fieldName) {
    final String prefix = AnnotationUtil.getDeclaredStringAttributeValue(annotation, "prefix");
    return prefix == null ? "set" + StringUtil.capitalize(fieldName)
                          : prefix.isEmpty() ? fieldName
                                             : prefix + StringUtil.capitalize(fieldName);
  }
}
