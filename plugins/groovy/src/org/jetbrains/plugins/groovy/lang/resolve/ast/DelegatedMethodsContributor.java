/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve.ast;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.ASTTransformContributors;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class DelegatedMethodsContributor extends ASTTransformContributors {

  @Override
  public void getMethods(@NotNull final GrTypeDefinition clazz, Collection<PsiMethod> collector) {
    final GrField[] fields = clazz.getFields();
    for (GrField field : fields) {
      final PsiAnnotation delegate = PsiImplUtil.getAnnotation(field, GroovyCommonClassNames.GROOVY_LANG_DELEGATE);
      if (delegate == null) continue;

      final PsiType type = field.getDeclaredType();
      if (!(type instanceof PsiClassType)) continue;

      final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
      final PsiClass psiClass = resolveResult.getElement();
      if (psiClass == null) continue;

      final boolean deprecated = shouldDelegateDeprecated(delegate);
      final List<PsiMethod> methods = getMethodsFromClassWithoutAST(psiClass);

      for (PsiMethod method : methods) {
        if (method.isConstructor()) continue;
        if (!deprecated && PsiImplUtil.getAnnotation(method, "java.lang.Deprecated") != null) continue;
        collector.add(generateDelegateMethod(method, clazz, resolveResult.getSubstitutor()));
      }
    }
  }

  private static List<PsiMethod> getMethodsFromClassWithoutAST(PsiClass psiClass) {
    if (psiClass instanceof GrTypeDefinition) {
      return ((GrTypeDefinition)psiClass).getBody().getMethods();
    }
    else {
      return Arrays.asList(psiClass.getMethods());
    }
  }

  private static boolean shouldDelegateDeprecated(PsiAnnotation delegate) {
    final PsiAnnotationParameterList parameterList = delegate.getParameterList();
    final PsiNameValuePair[] attributes = parameterList.getAttributes();
    for (PsiNameValuePair attribute : attributes) {
      final String name = attribute.getName();
      if ("deprecated".equals(name)) {
        final PsiAnnotationMemberValue value = attribute.getValue();
        if (value instanceof GrLiteral) {
          final Object innerValue = ((GrLiteral)value).getValue();
          if (innerValue instanceof Boolean) {
            return (Boolean)innerValue;
          }
        }
      }
    }
    return false;
  }

  private static PsiMethod generateDelegateMethod(PsiMethod method, PsiClass clazz, PsiSubstitutor substitutor) {
    final LightMethodBuilder builder = new LightMethodBuilder(clazz.getManager(), GroovyFileType.GROOVY_LANGUAGE, method.getName());
    builder.setContainingClass(clazz);
    builder.setReturnType(substitutor.substitute(method.getReturnType()));
    builder.setNavigationElement(method);
    builder.addModifier(PsiModifier.PUBLIC);
    final PsiParameter[] originalParameters = method.getParameterList().getParameters();

    final PsiClass containingClass = method.getContainingClass();
    boolean isRaw = containingClass != null && PsiUtil.isRawSubstitutor(containingClass, substitutor);
    if (isRaw) {
      PsiTypeParameter[] methodTypeParameters = method.getTypeParameters();
      substitutor = JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createRawSubstitutor(substitutor, methodTypeParameters);
    }

    for (int i = 0, originalParametersLength = originalParameters.length; i < originalParametersLength; i++) {
      PsiParameter originalParameter = originalParameters[i];
      PsiType type;
      if (isRaw) {
        type = TypeConversionUtil.erasure(substitutor.substitute(originalParameter.getType()));
      }
      else {
        type = substitutor.substitute(originalParameter.getType());
      }
      if (type == null) {
        type = PsiType.getJavaLangObject(clazz.getManager(), clazz.getResolveScope());
      }
      builder.addParameter(StringUtil.notNullize(originalParameter.getName(), "p" + i), type);
    }
    builder.setBaseIcon(GroovyIcons.METHOD);
    return builder;
  }
}
