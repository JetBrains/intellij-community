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
package org.jetbrains.plugins.groovy.lang.resolve.ast.bindable;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.resolve.ast.AstTransformContributor;

import java.util.Collection;
import java.util.List;

public class BindableTransformContributor extends AstTransformContributor {

  private static final String BINDABLE_FQN = "groovy.beans.Bindable";
  private static final String PCL_FQN = "java.beans.PropertyChangeListener";
  public static final String ORIGIN_INFO = "via @Bindable";

  private static boolean isApplicable(@NotNull GrTypeDefinition clazz) {
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(clazz, true, BINDABLE_FQN);
    if (annotation != null) return true;

    for (GrField method : clazz.getCodeFields()) {
      if (AnnotationUtil.findAnnotation(method, true, BINDABLE_FQN) != null) {
        return true;
      }
    }

    return false;
  }

  @Override
  public void collectMethods(@NotNull GrTypeDefinition clazz, Collection<PsiMethod> collector) {
    if (!isApplicable(clazz)) return;

    final PsiManager manager = clazz.getManager();
    final GlobalSearchScope scope = clazz.getResolveScope();

    final PsiType pclType = JavaPsiFacade.getInstance(clazz.getProject()).getElementFactory().createTypeByFQClassName(PCL_FQN, scope);
    final PsiArrayType pclArrayType = new PsiArrayType(pclType);
    final PsiType stringType = PsiType.getJavaLangString(manager, scope);
    final PsiType objectType = PsiType.getJavaLangObject(manager, scope);

    final List<LightMethodBuilder> methods = ContainerUtil.newArrayList();

    methods.add(
      new LightMethodBuilder(manager, GroovyLanguage.INSTANCE, "addPropertyChangeListener")
        .setMethodReturnType(PsiType.VOID)
        .addParameter("listener", pclType)
    );

    methods.add(
      new LightMethodBuilder(manager, GroovyLanguage.INSTANCE, "addPropertyChangeListener")
        .setMethodReturnType(PsiType.VOID)
        .addParameter("name", stringType)
        .addParameter("listener", pclType)
    );

    methods.add(
      new LightMethodBuilder(manager, GroovyLanguage.INSTANCE, "removePropertyChangeListener")
        .setMethodReturnType(PsiType.VOID)
        .addParameter("listener", pclType)
    );

    methods.add(
      new LightMethodBuilder(manager, GroovyLanguage.INSTANCE, "removePropertyChangeListener")
        .setMethodReturnType(PsiType.VOID)
        .addParameter("name", stringType)
        .addParameter("listener", pclType)
    );

    methods.add(
      new LightMethodBuilder(manager, GroovyLanguage.INSTANCE, "firePropertyChange")
        .setMethodReturnType(PsiType.VOID)
        .addParameter("name", stringType)
        .addParameter("oldValue", objectType)
        .addParameter("newValue", objectType)
    );

    methods.add(
      new LightMethodBuilder(manager, GroovyLanguage.INSTANCE, "getPropertyChangeListeners")
        .setMethodReturnType(pclArrayType)
    );

    methods.add(
      new LightMethodBuilder(manager, GroovyLanguage.INSTANCE, "getPropertyChangeListeners")
        .setMethodReturnType(pclArrayType)
        .addParameter("name", stringType)
    );

    for (LightMethodBuilder method : methods) {
      method.setContainingClass(clazz);
      method.addModifier(PsiModifier.PUBLIC);
      method.setOriginInfo(ORIGIN_INFO);
    }

    collector.addAll(methods);
  }
}
