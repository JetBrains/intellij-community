/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.intellij.plugins.intelliLang;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import gnu.trove.THashSet;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.config.AbstractTagInjection;
import org.intellij.plugins.intelliLang.inject.config.MethodParameterInjection;
import org.intellij.plugins.intelliLang.inject.config.XmlAttributeInjection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * @author Gregory.Shrago
 */
public class PatternBasedInjectionHelper {

  private PatternBasedInjectionHelper() {
  }

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
  public static ElementPattern<PsiElement> createElementPattern(final String text, final String displayName, final String supportId) {
    return createElementPatternNoException(text, displayName, supportId);
  }

  //@Nullable
  //public static ElementPattern<PsiElement> createElementPatternGroovy(final String text, final String displayName, final String supportId) {
  //  final Binding binding = new Binding();
  //  final ArrayList<MetaMethod> metaMethods = new ArrayList<MetaMethod>();
  //  for (Class aClass : getPatternClasses(supportId)) {
  //    // walk super classes as well?
  //    for (CachedMethod method : ReflectionCache.getCachedClass(aClass).getMethods()) {
  //      if (!Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers()) || Modifier.isAbstract(method.getModifiers())) continue;
  //      metaMethods.add(method);
  //    }
  //  }
  //
  //  final ExpandoMetaClass metaClass = new ExpandoMetaClass(Object.class, false, metaMethods.toArray(new MetaMethod[metaMethods.size()]));
  //  final GroovyShell shell = new GroovyShell(binding);
  //  try {
  //    final Script script = shell.parse("return " + text);
  //    metaClass.initialize();
  //    script.setMetaClass(metaClass);
  //    final Object value = script.run();
  //    return value instanceof ElementPattern ? (ElementPattern<PsiElement>)value : null;
  //  }
  //  catch (GroovyRuntimeException ex) {
  //    Configuration.LOG.warn("error processing place: "+displayName+" ["+text+"]", ex);
  //  }
  //  return null;
  //}

  private static Class[] getPatternClasses(final String supportId) {
    final ArrayList<Class> patternClasses = new ArrayList<Class>();
    for (LanguageInjectionSupport support : Extensions.getExtensions(LanguageInjectionSupport.EP_NAME)) {
      if (supportId == null || supportId.equals(support.getId())) {
        patternClasses.addAll(Arrays.asList(support.getPatternClasses()));
      }
    }
    return patternClasses.toArray(new Class[patternClasses.size()]);
  }

  // without Groovy
  @Nullable
  public static ElementPattern<PsiElement> createElementPatternNoException(final String text, final String displayName, final String supportId) {
    try {
      return compileElementPattern(text, supportId);
    }
    catch (Exception ex) {
      final Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
      Configuration.LOG.warn("error processing place: " + displayName + " [" + text + "]", cause);
      return null;
    }
  }

  public static ElementPattern<PsiElement> compileElementPattern(final String text, final String supportId) {  
    final Class[] patternClasses = getPatternClasses(supportId);
    final Set<Method> staticMethods = new THashSet<Method>(ContainerUtil.concat(patternClasses, new Function<Class, Collection<? extends Method>>() {
      public Collection<Method> fun(final Class aClass) {
        return ContainerUtil.findAll(ReflectionCache.getMethods(aClass), new Condition<Method>() {
          public boolean value(final Method method) {
            return Modifier.isStatic(method.getModifiers())
                   && Modifier.isPublic(method.getModifiers())
                   && !Modifier.isAbstract(method.getModifiers())
                   && ElementPattern.class.isAssignableFrom(method.getReturnType());
          }
        });
      }
    }));
    return createElementPatternNoGroovy(text, new Function<Frame, Object>() {
      public Object fun(final Frame frame) {
        try {
          return invokeMethod(frame.target, frame.methodName, frame.params.toArray(), staticMethods);
        }
        catch (Throwable throwable) {
          throw new IllegalArgumentException(text, throwable);
        }
      }
    });
  }

  private static enum State {
    init, name, name_end,
    param_start, param_end, literal,
    invoke, invoke_end
  }

  private static class Frame {
    State state = State.init;
    Object target;
    String methodName;
    ArrayList<Object> params = new ArrayList<Object>();
  }

  public static ElementPattern<PsiElement> createElementPatternNoGroovy(final String text, final Function<Frame, Object> executor) {
    final Stack<Frame> stack = new Stack<Frame>();
    int curPos = 0;
    Frame curFrame = new Frame();
    Object curResult = null;
    final StringBuilder curString = new StringBuilder();
    while (true) {
      if (curPos > text.length()) break;
      final char ch = curPos++ < text.length()? text.charAt(curPos-1) : 0;
      switch (curFrame.state) {
        case init:
          if (Character.isJavaIdentifierStart(ch)) {
            curString.append(ch);
            curFrame.state = State.name;
          }
          else {
            throw new IllegalStateException("method call expected");
          }
          break;
        case name:
          if (Character.isJavaIdentifierPart(ch)) {
            curString.append(ch);
          }
          else if (ch == '(' || Character.isWhitespace(ch)) {
            curFrame.methodName = curString.toString();
            curString.setLength(0);
            curFrame.state = ch == '('? State.param_start : State.name_end;
          }
          else {
            throw new IllegalStateException("'"+curString+ch+"' method name start is invalid");
          }
          break;
        case name_end:
          if (ch == '(') {
            curFrame.state = State.param_start;
          }
          else if (!Character.isWhitespace(ch)) {
            throw new IllegalStateException("'(' expected after '"+curFrame.methodName+"'");
          }
          break;
        case param_start:
          if (Character.isWhitespace(ch)) {
          }
          else if (Character.isDigit(ch) || ch == '\"') {
            curFrame.state = State.literal;
            curString.append(ch);
          }
          else if (ch == ')') {
            curFrame.state = State.invoke;
          }
          else if (Character.isJavaIdentifierStart(ch)) {
            curString.append(ch);
            stack.push(curFrame);
            curFrame = new Frame();
            curFrame.state = State.name;
          }
          else {
            throw new IllegalStateException("expression expected in '" + curFrame.methodName + "' call");
          }
          break;
        case param_end:
          if (ch == ')') {
            curFrame.state = State.invoke;
          }
          else if (ch == ',') {
            curFrame.state = State.param_start;
          }
          else if (!Character.isWhitespace(ch)) {
            throw new IllegalStateException("')' or ',' expected in '" + curFrame.methodName + "' call");
          }
          break;
        case literal:
          if (curString.charAt(0) == '\"') {
            curString.append(ch);
            if (ch == '\"') {
              curFrame.params.add(makeParam(curString.toString()));
              curString.setLength(0);
              curFrame.state = State.param_end;
            }
          }
          else if (Character.isWhitespace(ch) || ch == ',' || ch == ')') {
            curFrame.params.add(makeParam(curString.toString()));
            curString.setLength(0);
            curFrame.state = ch == ')' ? State.invoke :
                             ch == ',' ? State.param_start : State.param_end;
          }
          else {
            curString.append(ch);
          }
          break;
        case invoke:
          curResult = executor.fun(curFrame);
          if (ch == 0 && stack.isEmpty()) {
            return (ElementPattern<PsiElement>)curResult;
          }
          else if (ch == '.') {
            curFrame = new Frame();
            curFrame.target = curResult;
            curFrame.state = State.init;
            curResult = null;
          }
          else if (ch == ',' || ch == ')') {
            curFrame = stack.pop();
            curFrame.params.add(curResult);
            curResult = null;
            curFrame.state = ch == ')' ? State.invoke : State.param_start;
          }
          else if (Character.isWhitespace(ch)) {
            curFrame.state = State.invoke_end;
          }
          else {
            throw new IllegalStateException((stack.isEmpty()? "'.' or <eof>" : "'.' or ')'")
                                            + "expected after '" + curFrame.methodName + "' call");
          }
          break;
        case invoke_end:
          if (ch == ')') {
            curFrame.state = State.invoke;
          }
          else if (ch == ',') {
            curFrame.state = State.param_start;
          }
          else if (ch == '.') {
            curFrame = new Frame();
            curFrame.target = curResult;
            curFrame.state = State.init;
            curResult = null;
          }
          else if (!Character.isWhitespace(ch)) {
            throw new IllegalStateException((stack.isEmpty()? "'.' or <eof>" : "'.' or ')'")
                                            + "expected after '" + curFrame.methodName + "' call");
          }
          break;
      }
    }
    return null;
  }

  private static Object makeParam(final String s) {
    if (s.length() > 2 && s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length()-1);
    try {
      return Integer.valueOf(s);
    }
    catch (NumberFormatException e) {}
    return s;
  }

  public static Class<?> getNonPrimitiveType(final Class<?> type) {
    if (!type.isPrimitive()) return type;
    if (type == boolean.class) return Boolean.class;
    if (type == byte.class) return Byte.class;
    if (type == short.class) return Short.class;
    if (type == int.class) return Integer.class;
    if (type == long.class) return Long.class;
    if (type == float.class) return Float.class;
    if (type == double.class) return Double.class;
    if (type == char.class) return Character.class;
    return type;
  }

  private static Object invokeMethod(@Nullable final Object target, final String methodName, final Object[] arguments, final Collection<Method> staticMethods) throws Throwable {
    main: for (Method method : target == null? staticMethods : Arrays.asList(target.getClass().getMethods())) {
      if (!methodName.equals(method.getName())) continue;
      final Class<?>[] parameterTypes = method.getParameterTypes();
      if (!method.isVarArgs() && parameterTypes.length != arguments.length) continue;
      boolean performArgConversion = false;
      for (int i = 0, parameterTypesLength = parameterTypes.length; i < arguments.length; i++) {
        final Class<?> type = getNonPrimitiveType(i < parameterTypesLength ? parameterTypes[i] : parameterTypes[parameterTypesLength - 1]);
        final Object argument = arguments[i];
        final Class<?> componentType =
          method.isVarArgs() && i < parameterTypesLength - 1 ? null : parameterTypes[parameterTypesLength - 1].getComponentType();
        if (argument != null) {
          if (!type.isInstance(argument)) {
            if ((componentType == null || !componentType.isInstance(argument))) continue main;
            else performArgConversion = true;
          }
        }
      }
      if (parameterTypes.length > arguments.length) {
        performArgConversion = true;
      }
      try {
        final Object[] newArgs;
        if (!performArgConversion) newArgs = arguments;
        else {
          newArgs = new Object[parameterTypes.length];
          System.arraycopy(arguments, 0, newArgs, 0, parameterTypes.length - 1);
          final Object[] varArgs = (Object[])Array.newInstance(parameterTypes[parameterTypes.length - 1].getComponentType(), arguments.length - parameterTypes.length + 1);
          System.arraycopy(arguments, parameterTypes.length - 1, varArgs, 0, varArgs.length);
          newArgs[parameterTypes.length - 1] = varArgs;
        }
        return method.invoke(target, newArgs);
      }
      catch (InvocationTargetException e) {
        throw e.getTargetException();
      }
    }
    throw new NoSuchMethodException("unknown symbol: "+methodName + "(" + StringUtil.join(arguments, new Function<Object, String>() {
      public String fun(Object o) {
        return String.valueOf(o);
      }
    }, ", ")+")");
  }

}
