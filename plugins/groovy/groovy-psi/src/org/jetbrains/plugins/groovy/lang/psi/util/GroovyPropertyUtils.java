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
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.AccessorResolverProcessor;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author ilyas
 */
public class GroovyPropertyUtils {
  private static final Logger LOG = Logger.getInstance(GroovyPropertyUtils.class);

  public static final String IS_PREFIX = "is";
  public static final String GET_PREFIX = "get";
  public static final String SET_PREFIX = "set";

  private GroovyPropertyUtils() {
  }

  public static PsiMethod[] getAllSettersByField(PsiField field) {
    return getAllSetters(field.getContainingClass(), field.getName(), field.hasModifierProperty(PsiModifier.STATIC), false);
  }

  @NotNull
  public static PsiMethod[] getAllGettersByField(PsiField field) {
    return getAllGetters(field.getContainingClass(), field.getName(), field.hasModifierProperty(PsiModifier.STATIC), false);
  }

  @Nullable
  public static PsiMethod findSetterForField(PsiField field) {
    final PsiClass containingClass = field.getContainingClass();
    final String propertyName = field.getName();
    final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    return findPropertySetter(containingClass, propertyName, isStatic, true);
  }

  @Nullable
  public static PsiMethod findGetterForField(PsiField field) {
    final PsiClass containingClass = field.getContainingClass();
    final String propertyName = field.getName();
    final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    return findPropertyGetter(containingClass, propertyName, isStatic, true);
  }

  @Nullable
  public static PsiMethod findPropertySetter(@Nullable PsiType type, String propertyName, @NotNull GroovyPsiElement context) {
    final String setterName = getSetterName(propertyName);
    if (type == null) {
      final GrExpression fromText = GroovyPsiElementFactory.getInstance(context.getProject()).createExpressionFromText("this", context);
      return findPropertySetter(fromText.getType(), propertyName, context);
    }

    final AccessorResolverProcessor processor = new AccessorResolverProcessor(setterName, propertyName, context, false);
    ResolveUtil.processAllDeclarations(type, processor, ResolveState.initial(), context);
    final GroovyResolveResult[] setterCandidates = processor.getCandidates();
    return PsiImplUtil.extractUniqueElement(setterCandidates);
  }

  @Nullable
  public static PsiMethod findPropertySetter(PsiClass aClass, String propertyName, boolean isStatic, boolean checkSuperClasses) {
    if (aClass == null) return null;
    PsiMethod[] methods;
    if (checkSuperClasses) {
      methods = aClass.getAllMethods();
    }
    else {
      methods = aClass.getMethods();
    }

    for (PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC) != isStatic) continue;

      if (isSimplePropertySetter(method)) {
        if (propertyName.equals(getPropertyNameBySetter(method))) {
          return method;
        }
      }
    }

    return null;
  }

  @NotNull
  public static PsiMethod[] getAllGetters(PsiClass aClass, @NotNull String propertyName, boolean isStatic, boolean checkSuperClasses) {
    if (aClass == null) return PsiMethod.EMPTY_ARRAY;
    PsiMethod[] methods;
    if (checkSuperClasses) {
      methods = aClass.getAllMethods();
    }
    else {
      methods = aClass.getMethods();
    }

    List<PsiMethod> result = new ArrayList<>();
    for (PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC) != isStatic) continue;

      if (isSimplePropertyGetter(method)) {
        if (propertyName.equals(getPropertyNameByGetter(method))) {
          result.add(method);
        }
      }
    }

    return result.toArray(new PsiMethod[result.size()]);
  }

  @NotNull
  public static PsiMethod[] getAllSetters(PsiClass aClass, @NotNull String propertyName, boolean isStatic, boolean checkSuperClasses) {
    if (aClass == null) return PsiMethod.EMPTY_ARRAY;
    PsiMethod[] methods;
    if (checkSuperClasses) {
      methods = aClass.getAllMethods();
    }
    else {
      methods = aClass.getMethods();
    }

    List<PsiMethod> result = new ArrayList<>();
    for (PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC) != isStatic) continue;

      if (isSimplePropertySetter(method)) {
        if (propertyName.equals(getPropertyNameBySetter(method))) {
          result.add(method);
        }
      }
    }

    return result.toArray(new PsiMethod[result.size()]);
  }


  @Nullable
  public static PsiMethod findPropertyGetter(@Nullable PsiClass aClass,
                                             String propertyName,
                                             @Nullable Boolean isStatic,
                                             boolean checkSuperClasses) {
    if (aClass == null) return null;
    PsiMethod[] methods;
    if (checkSuperClasses) {
      methods = aClass.getAllMethods();
    }
    else {
      methods = aClass.getMethods();
    }

    for (PsiMethod method : methods) {
      if (isStatic != null && method.hasModifierProperty(PsiModifier.STATIC) != isStatic) continue;

      if (isSimplePropertyGetter(method)) {
        if (propertyName.equals(getPropertyNameByGetter(method))) {
          return method;
        }
      }
    }

    return null;
  }

  public static boolean isSimplePropertyAccessor(PsiMethod method) {
    return isSimplePropertyGetter(method) || isSimplePropertySetter(method);
  }//do not check return type

  public static boolean isSimplePropertyGetter(PsiMethod method) {
    return isSimplePropertyGetter(method, null);
  }//do not check return type

  public static boolean isSimplePropertyGetter(PsiMethod method, @Nullable String propertyName) {
    if (method == null || method.isConstructor()) return false;
    if (method.getParameterList().getParametersCount() != 0) return false;
    if (!isGetterName(method.getName())) return false;
    if (method.getName().startsWith(IS_PREFIX) && !PsiType.BOOLEAN.equals(method.getReturnType())) {
      return false;
    }
    if (PsiType.VOID.equals(method.getReturnType())) return false;
    if (propertyName == null) return true;

    final String byGetter = getPropertyNameByGetter(method);
    return propertyName.equals(byGetter) || (!isPropertyName(byGetter) && propertyName.equals(
      getPropertyNameByGetterName(method.getName(), PsiType.BOOLEAN.equals(method.getReturnType()))));
  }

  public static boolean isSimplePropertySetter(PsiMethod method) {
    return isSimplePropertySetter(method, null);
  }

  public static boolean isSimplePropertySetter(PsiMethod method, @Nullable String propertyName) {
    if (method == null || method.isConstructor()) return false;
    if (method.getParameterList().getParametersCount() != 1) return false;
    if (!isSetterName(method.getName())) return false;
    if (propertyName==null) return true;

    final String bySetter = getPropertyNameBySetter(method);
    return propertyName.equals(bySetter) || (!isPropertyName(bySetter) && propertyName.equals(getPropertyNameBySetterName(method.getName())));
  }

  @Nullable
  public static String getPropertyNameByGetter(PsiMethod getterMethod) {
    if (getterMethod instanceof GrAccessorMethod) {
      return ((GrAccessorMethod)getterMethod).getProperty().getName();
    }

    @NonNls String methodName = getterMethod.getName();
    final boolean isPropertyBoolean = PsiType.BOOLEAN.equals(getterMethod.getReturnType());
    return getPropertyNameByGetterName(methodName, isPropertyBoolean);
  }

  @Nullable
  public static String getPropertyNameByGetterName(@NotNull String methodName, boolean canBeBoolean) {
    if (methodName.startsWith(GET_PREFIX) && methodName.length() > 3) {
      return decapitalize(methodName.substring(3));
    }
    if (canBeBoolean && methodName.startsWith(IS_PREFIX) && methodName.length() > 2) {
      return decapitalize(methodName.substring(2));
    }
    return null;
  }

  @Nullable
  public static String getPropertyNameBySetter(PsiMethod setterMethod) {
    if (setterMethod instanceof GrAccessorMethod) {
      return ((GrAccessorMethod)setterMethod).getProperty().getName();
    }

    @NonNls String methodName = setterMethod.getName();
    return getPropertyNameBySetterName(methodName);
  }

  @Nullable
  public static String getPropertyNameBySetterName(@NotNull String methodName) {
    if (methodName.startsWith(SET_PREFIX) && methodName.length() > 3) {
      return StringUtil.decapitalize(methodName.substring(3));
    }
    else {
      return null;
    }
  }

  @Nullable
  public static String getPropertyNameByAccessorName(String accessorName) {
    if (isGetterName(accessorName)) {
      return getPropertyNameByGetterName(accessorName, true);
    }
    else if (isSetterName(accessorName)) {
      return getPropertyNameBySetterName(accessorName);
    }
    return null;
  }

  @Nullable
  public static String getPropertyName(PsiMethod accessor) {
    if (isSimplePropertyGetter(accessor)) return getPropertyNameByGetter(accessor);
    if (isSimplePropertySetter(accessor)) return getPropertyNameBySetter(accessor);
    return null;
  }

  public static boolean isGetterName(@NotNull String name) {
    int prefixLength;
    if (name.startsWith(GET_PREFIX)) {
      prefixLength = 3;
    }
    else if (name.startsWith(IS_PREFIX)) {
      prefixLength = 2;
    }
    else {
      return false;
    }

    if (name.length() == prefixLength) return false;

    if (isUpperCase(name.charAt(prefixLength))) return true;

    return name.length() > prefixLength + 1 && isUpperCase(name.charAt(prefixLength + 1));
  }

  public static String getGetterNameNonBoolean(@NotNull String name) {
    return getAccessorName(GET_PREFIX, name);
  }

  public static String getGetterNameBoolean(@NotNull String name) {
    return getAccessorName(IS_PREFIX, name);
  }

  public static String getSetterName(@NotNull String name) {
    return getAccessorName("set", name);
  }

  public static String getAccessorName(String prefix, String name) {
    if (name.isEmpty()) return prefix;

    StringBuilder sb = new StringBuilder();
    sb.append(prefix);
    if (name.length() > 1 && Character.isUpperCase(name.charAt(1))) {
      sb.append(name);
    }
    else {
      sb.append(Character.toUpperCase(name.charAt(0)));
      sb.append(name, 1, name.length());
    }

    return sb.toString();
  }

  /**
   * Returns getter names in priority order
   * @param name property name
   * @return getter names
   */
  public static String[] suggestGettersName(@NotNull String name) {
    return new String[]{getGetterNameBoolean(name), getGetterNameNonBoolean(name)};
  }

  public static boolean isPropertyName(String name) {
    if (name.isEmpty()) return false;
    if (Character.isUpperCase(name.charAt(0)) && (name.length() == 1 || !Character.isUpperCase(name.charAt(1)))) return false;
    return true;
  }

  public static String[] suggestSettersName(@NotNull String name) {
    return new String[]{getSetterName(name)};
  }

  public static boolean isSetterName(String name) {
    return name != null
           && name.startsWith(SET_PREFIX)
           && name.length() > 3
           && (isUpperCase(name.charAt(3)) || (name.length() > 4 && isUpperCase(name.charAt(3))));
  }

  public static boolean isProperty(@Nullable PsiClass aClass, @Nullable String propertyName, boolean isStatic) {
    if (aClass == null || propertyName == null) return false;
    final PsiField field = aClass.findFieldByName(propertyName, true);
    if (field instanceof GrField && ((GrField)field).isProperty() && field.hasModifierProperty(PsiModifier.STATIC) == isStatic) return true;

    final PsiMethod getter = findPropertyGetter(aClass, propertyName, isStatic, true);
    if (getter != null && getter.hasModifierProperty(PsiModifier.PUBLIC)) return true;

    final PsiMethod setter = findPropertySetter(aClass, propertyName, isStatic, true);
    return setter != null && setter.hasModifierProperty(PsiModifier.PUBLIC);
  }

  public static boolean isProperty(GrField field) {
    final PsiClass clazz = field.getContainingClass();
    return isProperty(clazz, field.getName(), field.hasModifierProperty(PsiModifier.STATIC));
  }

  private static boolean isUpperCase(char c) {
    return Character.toUpperCase(c) == c;
  }

  /*public static boolean canBePropertyName(String name) {
    return !(name.length() > 1 && Character.isUpperCase(name.charAt(1)) && Character.isLowerCase(name.charAt(0)));
  }*/

  public static String capitalize(String s) {
    if (s.isEmpty()) return s;
    if (s.length() == 1) return s.toUpperCase();
    if (Character.isUpperCase(s.charAt(1))) return s;
    final char[] chars = s.toCharArray();
    chars[0] = Character.toUpperCase(chars[0]);
    return new String(chars);
  }

  public static String decapitalize(String s) {
    return Introspector.decapitalize(s);
  }

  @Nullable
  public static PsiField findFieldForAccessor(PsiMethod accessor, boolean checkSuperClasses) {
    final PsiClass psiClass = accessor.getContainingClass();
    if (psiClass == null) return null;
    PsiField field = null;
    if (!checkSuperClasses) {
      field = psiClass.findFieldByName(getPropertyNameByAccessorName(accessor.getName()), true);
    }
    else {
      final String name = getPropertyNameByAccessorName(accessor.getName());
      assert name != null;
      final PsiField[] allFields = psiClass.getAllFields();
      for (PsiField psiField : allFields) {
        if (name.equals(psiField.getName())) {
          field = psiField;
          break;
        }
      }
    }
    if (field == null) return null;
    if (field.hasModifierProperty(PsiModifier.STATIC) == accessor.hasModifierProperty(PsiModifier.STATIC)) {
      return field;
    }
    return null;
  }

  @Nullable
  public static String getGetterPrefix(PsiMethod getter) {
    final String name = getter.getName();
    if (name.startsWith(GET_PREFIX)) return GET_PREFIX;
    if (name.startsWith(IS_PREFIX)) return IS_PREFIX;

    return null;
  }

  @Nullable
  public static String getSetterPrefix(PsiMethod setter) {
    if (setter.getName().startsWith(SET_PREFIX)) return SET_PREFIX;
    return null;
  }

  @Nullable
  public static String getAccessorPrefix(PsiMethod method) {
    final String prefix = getGetterPrefix(method);
    if (prefix != null) return prefix;

    return getSetterPrefix(method);
  }

  public static boolean isAccessorFor(PsiMethod accessor, PsiField field) {
    final String accessorName = accessor.getName();
    final String fieldName = field.getName();
    if (!ArrayUtil.contains(accessorName, suggestGettersName(fieldName)) &&
        !ArrayUtil.contains(accessorName, suggestSettersName(fieldName))) {
      return false;
    }
    final PsiClass accessorClass = accessor.getContainingClass();
    final PsiClass fieldClass = field.getContainingClass();
    if (!field.getManager().areElementsEquivalent(accessorClass, fieldClass)) return false;
    return accessor.hasModifierProperty(PsiModifier.STATIC) == field.hasModifierProperty(PsiModifier.STATIC);
  }

  public static List<GrAccessorMethod> getFieldAccessors(GrField field) {
    List<GrAccessorMethod> accessors = new ArrayList<>();
    final GrAccessorMethod[] getters = field.getGetters();
    Collections.addAll(accessors, getters);
    final GrAccessorMethod setter = field.getSetter();
    if (setter != null) accessors.add(setter);
    return accessors;
  }

  public static GrMethod generateGetterPrototype(PsiField field) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(field.getProject());
    String name = field.getName();
    String getName = getGetterNameNonBoolean(field.getName());
    try {
      PsiType type = field instanceof GrField ? ((GrField)field).getDeclaredType() : field.getType();
      GrMethod getter = factory.createMethod(getName, type);
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        PsiUtil.setModifierProperty(getter, PsiModifier.STATIC, true);
      }

      annotateWithNullableStuff(field, getter);

      GrCodeBlock body = factory.createMethodBodyFromText("\nreturn " + name + "\n");
      getter.getBlock().replace(body);
      return getter;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  public static GrMethod generateSetterPrototype(PsiField field) {
    Project project = field.getProject();
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

    String name = field.getName();
    boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    VariableKind kind = codeStyleManager.getVariableKind(field);
    String propertyName = codeStyleManager.variableNameToPropertyName(name, kind);
    String setName = getSetterName(field.getName());

    final PsiClass containingClass = field.getContainingClass();
    try {
      GrMethod setMethod = factory.createMethod(setName, PsiType.VOID);
      String parameterName = codeStyleManager.propertyNameToVariableName(propertyName, VariableKind.PARAMETER);
      final PsiType type = field instanceof GrField ? ((GrField)field).getDeclaredType() : field.getType();
      GrParameter param = factory.createParameter(parameterName, type);

      annotateWithNullableStuff(field, param);

      setMethod.getParameterList().add(param);
      PsiUtil.setModifierProperty(setMethod, PsiModifier.STATIC, isStatic);

      @NonNls StringBuilder builder = new StringBuilder();
      if (name.equals(parameterName)) {
        if (!isStatic) {
          builder.append("this.");
        }
        else {
          String className = containingClass.getName();
          if (className != null) {
            builder.append(className);
            builder.append(".");
          }
        }
      }
      builder.append(name);
      builder.append("=");
      builder.append(parameterName);
      builder.append("\n");
      GrCodeBlock body = factory.createMethodBodyFromText(builder.toString());
      setMethod.getBlock().replace(body);
      return setMethod;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  @SuppressWarnings("MagicConstant")
  private static void annotateWithNullableStuff(PsiModifierListOwner original,
                                                PsiModifierListOwner generated) throws IncorrectOperationException {
    NullableNotNullManager.getInstance(original.getProject()).copyNullableOrNotNullAnnotation(original, generated);

    PsiModifierList modifierList = generated.getModifierList();
    if (modifierList != null && modifierList.hasExplicitModifier(GrModifier.DEF)) {
      LOG.assertTrue(modifierList instanceof GrModifierList);
      if (modifierList.getAnnotations().length > 0 || ((GrModifierList)modifierList).getModifiers().length > 1) {
        modifierList.setModifierProperty(GrModifier.DEF, false);
      }
    }
  }
}
