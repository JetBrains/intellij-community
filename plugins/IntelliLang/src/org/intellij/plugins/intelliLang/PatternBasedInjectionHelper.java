/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.intellij.plugins.intelliLang;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiElement;
import groovy.lang.*;
import org.codehaus.groovy.reflection.CachedMethod;
import org.codehaus.groovy.reflection.ReflectionCache;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.config.AbstractTagInjection;
import org.intellij.plugins.intelliLang.inject.config.MethodParameterInjection;
import org.intellij.plugins.intelliLang.inject.config.XmlAttributeInjection;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author Gregory.Shrago
 */
public class PatternBasedInjectionHelper {
  private PatternBasedInjectionHelper() {
  }

  /*
   * Pattern:
   * . Operation took -78% longer than expected. Expected on my machine: 117465. Actual: 25360. Expected on Etalon machine: 150000; Actual on Etalon: 32383
   * . Operation took -87% longer than expected. Expected on my machine: 122945. Actual: 15126. Expected on Etalon machine: 150000; Actual on Etalon: 18454
   * . Operation took -85% longer than expected. Expected on my machine: 117465. Actual: 17203. Expected on Etalon machine: 150000; Actual on Etalon: 21967
   *  Old:
   * . Operation took -87% longer than expected. Expected on my machine: 117808. Actual: 14391. Expected on Etalon machine: 150000; Actual on Etalon: 18323 
   */

  //private static ElementPattern<PsiElement> createPatternFor(final MethodParameterInjection injection) {
  //  final ArrayList<ElementPattern<? extends PsiElement>> list = new ArrayList<ElementPattern<? extends PsiElement>>();
  //  final String className = injection.getClassName();
  //  for (MethodParameterInjection.MethodInfo info : injection.getMethodInfos()) {
  //    final String methodName = info.getMethodName();
  //    if (info.isReturnFlag()) {
  //      // place
  //      list.add(psiElement().inside(psiElement(JavaElementType.RETURN_STATEMENT).inside(psiMethod().withName(methodName).definedInClass(className))));
  //      // and owner:
  //      list.add(psiMethod().withName(methodName).definedInClass(className));
  //    }
  //    final boolean[] paramFlags = info.getParamFlags();
  //    for (int i = 0, paramFlagsLength = paramFlags.length; i < paramFlagsLength; i++) {
  //      if (paramFlags[i]) {
  //        // place
  //        list.add(psiElement().methodCallParameter(i, psiMethod().withName(methodName).withParameterCount(paramFlagsLength).definedInClass(className)));
  //        // and owner:
  //        list.add(psiParameter().ofMethod(i, psiMethod().withName(methodName).withParameterCount(paramFlagsLength).definedInClass(className)));
  //      }
  //    }
  //  }
  //  return StandardPatterns.or(list.toArray(new ElementPattern[list.size()]));
  //}

  public static List<String> getPatternString(final MethodParameterInjection injection) {
    final ArrayList<String> list = new ArrayList<String>();
    final String className = injection.getClassName();
    for (MethodParameterInjection.MethodInfo info : injection.getMethodInfos()) {
      final boolean[] paramFlags = info.getParamFlags();
      final int paramFlagsLength = paramFlags.length;
      final String methodName = info.getMethodName();
      final String typesString = getParameterTypesString(info.getMethodSignature());
      if (info.isReturnFlag()) {
        list.add(getPatternStringForJavaPlace(methodName, typesString, -1, className));
      }
      for (int i = 0; i < paramFlagsLength; i++) {
        if (paramFlags[i]) {
          list.add(getPatternStringForJavaPlace(methodName, typesString, i, className));
        }
      }
    }
    return list;
  }

  public static String getParameterTypesString(final String signature) {
    @NonNls final StringBuilder sb = new StringBuilder();
    final StringTokenizer st = new StringTokenizer(signature, "(,)");
    //noinspection ForLoopThatDoesntUseLoopVariable
    for (int i = 0; st.hasMoreTokens(); i++) {
      final String token = st.nextToken().trim();
      if (i > 1) sb.append(", ");
      final int idx;
      if (i == 0) {
        // nothing
      }
      else {
        sb.append('\"');
        if ((idx = token.indexOf(' ')) > -1) {
          sb.append(token.substring(0, idx));
        }
        else {
          sb.append(token);
        }
        sb.append('\"');
      }
    }
    return sb.toString();
  }

  public static String getPatternStringForJavaPlace(final String methodName, final String parametersStrings, final int parameterIndex, final String className) {
    final StringBuilder sb = new StringBuilder();
    if (parameterIndex >= 0) {
      sb.append("psiParameter().ofMethod(").append(parameterIndex).append(", ");
    }
    sb.append("psiMethod().withName(\"").append(methodName)
      .append("\").withParameters(").append(parametersStrings)
      .append(").definedInClass(\"").append(className).append("\")");
    if (parameterIndex >= 0) {
      sb.append(")");
    }
    return sb.toString();
  }

  public static String getPatternString(final XmlAttributeInjection injection) {
    final String name = injection.getAttributeName();
    final String namespace = injection.getAttributeNamespace();
    final StringBuilder result = new StringBuilder("xmlAttribute()");
    if (StringUtil.isNotEmpty(name)) result.append(".withLocalName(string().matches(\"").append(name).append("\"))");
    if (StringUtil.isNotEmpty(namespace)) result.append(".withNamespace(string().matches(\"").append(namespace).append("\"))");
    if (StringUtil.isNotEmpty(injection.getTagName()) || StringUtil.isNotEmpty(injection.getTagNamespace())) {
      result.append(".inside(").append(getPatternString((AbstractTagInjection)injection)).append(")");
    }
    return result.toString();
  }

  public static String getPatternString(final AbstractTagInjection injection) {
    final String name = injection.getTagName();
    final String namespace = injection.getTagNamespace();
    final StringBuilder result = new StringBuilder("xmlTag()");
    if (StringUtil.isNotEmpty(name)) result.append(".withLocalName(string().matches(\"").append(name).append("\"))");
    if (StringUtil.isNotEmpty(namespace)) result.append(".withNamespace(string().matches(\"").append(namespace).append("\"))");
    return result.toString();
  }

  @Nullable
  public static ElementPattern<PsiElement> createElementPattern(final String text, final String displayName) {
    final Binding binding = new Binding();
    final ArrayList<Class> patternClasses = new ArrayList<Class>();
    patternClasses.add(StandardPatterns.class);
    patternClasses.add(PlatformPatterns.class);
    for (LanguageInjectionSupport support : Extensions.getExtensions(LanguageInjectionSupport.EP_NAME)) {
      patternClasses.addAll(Arrays.asList(support.getPatternClasses()));
    }
    final ArrayList<MetaMethod> metaMethods = new ArrayList<MetaMethod>();
    for (Class aClass : patternClasses) {
      // walk super classes as well?
      for (CachedMethod method : ReflectionCache.getCachedClass(aClass).getMethods()) {
        if (!Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers()) || Modifier.isAbstract(method.getModifiers())) continue;
        metaMethods.add(method);
      }
    }

    final ExpandoMetaClass metaClass = new ExpandoMetaClass(Object.class, false, metaMethods.toArray(new MetaMethod[metaMethods.size()]));
    final GroovyShell shell = new GroovyShell(binding);
    try {
      final Script script = shell.parse("return " + text);
      metaClass.initialize();
      script.setMetaClass(metaClass);
      final Object value = script.run();
      return value instanceof ElementPattern ? (ElementPattern<PsiElement>)value : null;
    }
    catch (GroovyRuntimeException ex) {
      Configuration.LOG.warn("error processing place: "+displayName+" ["+text+"]", ex);
    }
    return null;
  }
}
