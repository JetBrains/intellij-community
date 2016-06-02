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

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.util.PropertyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ast.builder.BuilderAnnotationContributor;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createType;
import static org.jetbrains.plugins.groovy.lang.resolve.ast.builder.strategy.DefaultBuilderStrategySupport.createBuildMethod;

public class ExternalBuilderStrategySupport extends BuilderAnnotationContributor {

  public static final String EXTERNAL_STRATEGY_NAME = "ExternalStrategy";

  @Override
  public void applyTransformation(@NotNull TransformationContext context) {
    PsiAnnotation annotation = PsiImplUtil.getAnnotation(context.getCodeClass(), BUILDER_FQN);
    if (!isApplicable(annotation, EXTERNAL_STRATEGY_NAME)) return;

    final PsiClass constructedClass = GrAnnotationUtil.inferClassAttribute(annotation, "forClass");
    if (constructedClass == null || "groovy.transform.Undefined.CLASS".equals(constructedClass.getQualifiedName())) return;

    if (constructedClass instanceof GrTypeDefinition) {
      for (GrField field : ((GrTypeDefinition)constructedClass).getCodeFields()) {
        context.addMethod(DefaultBuilderStrategySupport.createFieldSetter(context.getCodeClass(), field, annotation));
      }
    }
    else {
      for (PsiMethod setter : PropertyUtil.getAllProperties(constructedClass, true, false).values()) {
        final PsiMethod builderSetter = createFieldSetter(context.getCodeClass(), setter, annotation);
        if (builderSetter != null) context.addMethod(builderSetter);
      }
    }
    context.addMethod(createBuildMethod(annotation, createType(constructedClass)));
  }

  @Nullable
  public static LightMethodBuilder createFieldSetter(@NotNull PsiClass builderClass,
                                                     @NotNull PsiMethod setter,
                                                     @NotNull PsiAnnotation annotation) {
    final String name = PropertyUtil.getPropertyNameBySetter(setter);
    final PsiType type = PropertyUtil.getPropertyType(setter);
    if (type == null) return null;
    return DefaultBuilderStrategySupport.createFieldSetter(builderClass, name, type, annotation, setter);
  }
}
