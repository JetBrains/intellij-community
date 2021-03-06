// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.impl;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.DOCUMENTATION_DELEGATE_FQN;

public class BindableTransformationSupport implements AstTransformationSupport {

  @NlsSafe private static final String BINDABLE_FQN = "groovy.beans.Bindable";
  @NlsSafe private static final String PCL_FQN = "java.beans.PropertyChangeListener";
  @NlsSafe private static final String PCS_FQN = "java.beans.PropertyChangeSupport";
  @NonNls public static final String ORIGIN_INFO = "via @Bindable";

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
  public void applyTransformation(@NotNull TransformationContext context) {
    GrTypeDefinition clazz = context.getCodeClass();
    if (!isApplicable(clazz)) return;

    final PsiManager manager = clazz.getManager();
    final GlobalSearchScope scope = clazz.getResolveScope();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(clazz.getProject());

    final PsiType pclType = facade.getElementFactory().createTypeByFQClassName(PCL_FQN, scope);
    final PsiArrayType pclArrayType = new PsiArrayType(pclType);
    final PsiType stringType = PsiType.getJavaLangString(manager, scope);
    final PsiType objectType = PsiType.getJavaLangObject(manager, scope);

    final List<LightMethodBuilder> methods = new ArrayList<>();

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
      method.addModifier(PsiModifier.PUBLIC);
      method.setOriginInfo(ORIGIN_INFO);
      method.putUserData(DOCUMENTATION_DELEGATE_FQN, PCS_FQN);
    }

    context.addMethods(methods);
  }
}
