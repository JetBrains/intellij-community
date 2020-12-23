// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.ast.builder.strategy;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.util.PropertyUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ast.builder.BuilderAnnotationContributor;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

import java.util.Collection;

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
    boolean includeSuper = isIncludeSuperProperties(annotation);
    if (constructedClass instanceof GrTypeDefinition) {
      PsiField[] fields = getFields((GrTypeDefinition)constructedClass, includeSuper, context);
      for (PsiField field : fields) {
        context.addMethod(DefaultBuilderStrategySupport.createFieldSetter(context.getCodeClass(), field, annotation, context));
      }
    } else {
      Collection<PsiMethod> properties = PropertyUtilBase.getAllProperties(constructedClass, true, false, includeSuper).values();
      for (PsiMethod setter : properties) {
        final PsiMethod builderSetter = createFieldSetter(context, setter, annotation);
        if (builderSetter != null) context.addMethod(builderSetter);
      }
    }
    context.addMethod(createBuildMethod(annotation, createType(constructedClass)));
  }

  @Nullable
  public static LightMethodBuilder createFieldSetter(@NotNull TransformationContext context,
                                                     @NotNull PsiMethod setter,
                                                     @NotNull PsiAnnotation annotation) {
    PsiClass builderClass = context.getCodeClass();
    final String name = PropertyUtilBase.getPropertyNameBySetter(setter);
    final PsiType type = PropertyUtilBase.getPropertyType(setter);
    if (type == null) return null;
    return DefaultBuilderStrategySupport.createFieldSetter(builderClass, name, type, annotation, setter, context);
  }
}
