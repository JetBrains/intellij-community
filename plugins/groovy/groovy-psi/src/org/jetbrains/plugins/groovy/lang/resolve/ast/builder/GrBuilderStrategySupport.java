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
package org.jetbrains.plugins.groovy.lang.resolve.ast.builder;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ast.builder.strategy.DefaultBuilderStrategySupport;

import java.util.Collection;

public abstract class GrBuilderStrategySupport {

  public static final ExtensionPointName<GrBuilderStrategySupport> EP = ExtensionPointName.create("org.intellij.groovy.builderStrategySupport");
  public static final String BUILDER_FQN = "groovy.transform.builder.Builder";
  public static final String ORIGIN_INFO = "by @Builder";

  public static class Members {
    public final Collection<PsiMethod> methods = ContainerUtil.newArrayList();
    public final Collection<GrField> fields = ContainerUtil.newArrayList();
    public final Collection<PsiClass> classes = ContainerUtil.newArrayList();
    public void addFrom(Members other) {
      methods.addAll(other.methods);
      fields.addAll(other.fields);
      classes.addAll(other.classes);
    }
  }

  @NotNull
  public abstract Members process(GrTypeDefinition typeDefinition);

  @Nullable
  public static String getStrategy(PsiModifierListOwner annotatedMember) {
    final PsiAnnotation annotation = PsiImplUtil.getAnnotation(annotatedMember, BUILDER_FQN);
    if (annotation == null) return null;

    final PsiAnnotationMemberValue strategy = annotation.findDeclaredAttributeValue("builderStrategy");
    if (strategy instanceof GrReferenceExpression) {
      final PsiElement element = ((GrReferenceExpression)strategy).resolve();
      return element instanceof PsiClass ? ((PsiClass)element).getQualifiedName() : null;
    }

    return DefaultBuilderStrategySupport.DEFAULT_STRATEGY_FQN;
  }
}
