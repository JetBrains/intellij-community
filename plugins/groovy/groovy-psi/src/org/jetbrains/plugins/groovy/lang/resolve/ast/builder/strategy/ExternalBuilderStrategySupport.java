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

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ast.Members;
import org.jetbrains.plugins.groovy.lang.resolve.ast.builder.BuilderAnnotationContributor;

import static org.jetbrains.plugins.groovy.lang.resolve.ast.builder.strategy.DefaultBuilderStrategySupport.createBuildMethod;
import static org.jetbrains.plugins.groovy.lang.resolve.ast.builder.strategy.DefaultBuilderStrategySupport.createFieldSetter;

public class ExternalBuilderStrategySupport extends BuilderAnnotationContributor {

  public static final String EXTERNAL_STRATEGY_NAME = "ExternalStrategy";

  @NotNull
  @Override
  public Members collect(@NotNull GrTypeDefinition builderClass) {
    Pair<PsiAnnotation, GrTypeDefinition> definitionPair = getConstructedClass(builderClass);
    if (definitionPair == null) return Members.EMPTY;

    final PsiAnnotation annotation = definitionPair.first;
    final GrTypeDefinition typeDefinition = definitionPair.second;
    final Members result = Members.create();
    for (GrField field : typeDefinition.getCodeFields()) {
      result.getMethods().add(createFieldSetter(builderClass, field, annotation));
    }
    result.getMethods().add(createBuildMethod(annotation, createType(typeDefinition), builderClass));
    return result;
  }

  private static Pair<PsiAnnotation, GrTypeDefinition> getConstructedClass(GrTypeDefinition builderClass) {
    final PsiAnnotation annotation = PsiImplUtil.getAnnotation(builderClass, BUILDER_FQN);
    if (!isApplicable(annotation, EXTERNAL_STRATEGY_NAME)) return null;
    final PsiClass constructedClass = getClassAttributeValue(annotation, "forClass");
    if (!(constructedClass instanceof GrTypeDefinition)) return null;
    return Pair.create(annotation, (GrTypeDefinition)constructedClass);
  }
}
