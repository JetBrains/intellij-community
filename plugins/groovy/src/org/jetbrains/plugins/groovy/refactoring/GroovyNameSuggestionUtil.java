// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author ilyas
 */
public final class GroovyNameSuggestionUtil {

  private GroovyNameSuggestionUtil() {
  }

  public static String[] suggestVariableNames(GrExpression expr, NameValidator validator) {
    return suggestVariableNames(expr, validator, false);
  }

  public static String[] suggestVariableNames(@NotNull GrExpression expr, NameValidator validator, boolean forStaticVariable) {
    Set<String> possibleNames = new LinkedHashSet<>();
    PsiType type = expr.getType();
    generateNameByExpr(expr, possibleNames, validator, forStaticVariable);
    if (type != null && !PsiType.VOID.equals(type)) {
      generateVariableNameByTypeInner(type, possibleNames,validator);
    }

    possibleNames.remove("");
    if (possibleNames.isEmpty()) {
      possibleNames.add(validator.validateName("var", true));
    }
    return ArrayUtilRt.toStringArray(possibleNames);
  }

  public static String[] suggestVariableNameByType(PsiType type, NameValidator validator) {
    if (type == null) return ArrayUtilRt.EMPTY_STRING_ARRAY;
    Set<String> possibleNames = new LinkedHashSet<>();
    generateVariableNameByTypeInner(type, possibleNames, validator);
    return ArrayUtilRt.toStringArray(possibleNames);
  }

  private static void generateVariableNameByTypeInner(PsiType type, Set<String> possibleNames, NameValidator validator) {
    String unboxed = PsiTypesUtil.unboxIfPossible(type.getCanonicalText());
    if (unboxed != null && !unboxed.equals(type.getCanonicalText())) {
      String name = generateNameForBuiltInType(unboxed);
      name = validator.validateName(name, true);
      if (GroovyNamesUtil.isIdentifier(name)) {
        possibleNames.add(name);
      }
    }
    else if (type instanceof PsiIntersectionType) {
      for (PsiType psiType : ((PsiIntersectionType)type).getConjuncts()) {
        generateByType(psiType, possibleNames, validator);
      }
    }
    else {
      generateByType(type, possibleNames, validator);
    }
  }

  private static void generateNameByExpr(GrExpression expr, Set<String> possibleNames, NameValidator validator, boolean forStaticVariable) {
    if (expr instanceof GrReferenceExpression && ((GrReferenceExpression) expr).getReferenceName() != null) {
      if (PsiUtil.isThisReference(expr)) {
        possibleNames.add(validator.validateName("thisInstance", true));
      }
      if (PsiUtil.isSuperReference(expr)) {
        possibleNames.add(validator.validateName("superInstance", true));
      }
      GrReferenceExpression refExpr = (GrReferenceExpression) expr;
      String name = refExpr.getReferenceName();
      if (name != null && StringUtil.toUpperCase(name).equals(name)) {
        possibleNames.add(validator.validateName(StringUtil.toLowerCase(name), true));
      } else {
        generateCamelNames(possibleNames, validator, name);
      }
      if (expr.getText().equals(name)) {
        possibleNames.remove(name);
      }
    }
    if (expr instanceof GrMethodCallExpression) {
      generateNameByExpr(((GrMethodCallExpression) expr).getInvokedExpression(), possibleNames, validator, forStaticVariable);
    }
    if (expr instanceof GrLiteral) {
      final Object value = ((GrLiteral)expr).getValue();
      if (value instanceof String) {
        generateNameByString(possibleNames, (String)value, validator, forStaticVariable, expr.getProject());
      }
    }
  }

  private static void generateNameByString(Set<String> possibleNames,
                                           String value,
                                           NameValidator validator,
                                           boolean forStaticVariable,
                                           Project project) {
    if (!PsiNameHelper.getInstance(project).isIdentifier(value)) return;
    if (forStaticVariable) {
      StringBuilder buffer = new StringBuilder(value.length() + 10);
      char[] chars = new char[value.length()];
      value.getChars(0, value.length(), chars, 0);
      boolean wasLow = Character.isLowerCase(chars[0]);

      buffer.append(Character.toUpperCase(chars[0]));
      for (int i = 1; i < chars.length; i++) {
        if (Character.isUpperCase(chars[i])) {
          if (wasLow) {
            buffer.append('_');
            wasLow = false;
          }
        }
        else {
          wasLow = true;
        }

        buffer.append(Character.toUpperCase(chars[i]));
      }
      possibleNames.add(validator.validateName(buffer.toString(), true));
    }
    else {
      possibleNames.add(validator.validateName(value, true));
    }
  }

  private static void generateByType(PsiType type, Set<String> possibleNames, NameValidator validator) {
    String typeName = type.getPresentableText();
    generateNamesForCollectionType(type, possibleNames, validator);
    generateNamesForArrayType(type, possibleNames, validator);
    generateNamesForExceptions(type, possibleNames, validator);
    typeName = cleanTypeName(typeName);
    if (typeName.equals("String")) {
      possibleNames.add(validator.validateName("s", true));
    }
    if (typeName.equals("Closure")) {
      possibleNames.add(validator.validateName("cl", true));
    }
    if (StringUtil.toUpperCase(typeName).equals(typeName)) {
      possibleNames.add(validator.validateName(GroovyNamesUtil.deleteNonLetterFromString(StringUtil.toLowerCase(typeName)), true));
    } else if (!typeName.equals(StringUtil.toLowerCase(typeName))) {
      generateCamelNames(possibleNames, validator, typeName);
      possibleNames.remove(typeName);
    }
  }

  private static void generateNamesForExceptions(PsiType type, Set<String> possibleNames, NameValidator validator) {
    if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ERROR)) {
      possibleNames.add(validator.validateName("error", true));
    }
    else if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_EXCEPTION)) {
      possibleNames.add(validator.validateName("e", true));
    }
  }

  private static void generateNamesForArrayType(PsiType type, Set<String> possibleNames, NameValidator validator) {
    int arrayDim = type.getArrayDimensions();
    if (arrayDim == 0) return;
    PsiType deepType = type.getDeepComponentType();
    String candidateName = cleanTypeName(deepType.getPresentableText());
    if (deepType instanceof PsiClassType) {
      PsiClass clazz = ((PsiClassType) deepType).resolve();
      if (clazz == null) return;
      candidateName = GroovyNamesUtil.fromLowerLetter(clazz.getName());
    }
    candidateName = StringUtil.pluralize(GroovyNamesUtil.fromLowerLetter(candidateName));
    generateCamelNames(possibleNames, validator, candidateName);

    ArrayList<String> camelizedName = GroovyNamesUtil.camelizeString(candidateName);
    candidateName = camelizedName.get(camelizedName.size() - 1);
    candidateName = "arrayOf" + fromUpperLetter(candidateName);
    possibleNames.add(validator.validateName(candidateName, true));
  }

  private static void generateNamesForCollectionType(PsiType type, Set<String> possibleNames, NameValidator validator) {
    PsiType componentType = getCollectionComponentType(type, validator.getProject());
    if (!(type instanceof PsiClassType) || componentType == null) return;
    PsiClass clazz = ((PsiClassType) type).resolve();
    if (clazz == null) return;
    String collectionName = clazz.getName();
    assert collectionName != null;

    String componentName = cleanTypeName(componentType.getPresentableText());
    if (componentType instanceof PsiClassType) {
      PsiClassType classType = (PsiClassType) componentType;
      PsiClass psiClass = classType.resolve();
      if (psiClass == null) return;
      componentName = psiClass.getName();
    }

    assert componentName != null;
    String candidateName = StringUtil.pluralize(GroovyNamesUtil.fromLowerLetter(componentName));
    generateCamelNames(possibleNames, validator, candidateName);

    ArrayList<String> camelizedName = GroovyNamesUtil.camelizeString(candidateName);
    candidateName = camelizedName.get(camelizedName.size() - 1);
    candidateName = StringUtil.toLowerCase(collectionName) + "Of" + fromUpperLetter(candidateName);
    possibleNames.add(validator.validateName(candidateName, true));
  }

  @NotNull
  private static String cleanTypeName(@NotNull String typeName) {
    if (typeName.contains(".")) {
      typeName = typeName.substring(typeName.lastIndexOf(".") + 1);
    }
    if (typeName.contains("<")) {
      typeName = typeName.substring(0, typeName.indexOf("<"));
    }
    return typeName;
  }

  private static void generateCamelNames(Set<String> possibleNames, NameValidator validator, String typeName) {
    ArrayList<String> camelTokens = GroovyNamesUtil.camelizeString(typeName);
    Collections.reverse(camelTokens);
    if (!camelTokens.isEmpty()) {
      String possibleName = "";
      for (String camelToken : camelTokens) {
        possibleName = camelToken + fromUpperLetter(possibleName);
        String candidate = validator.validateName(possibleName, true);
        // todo generify
        if (candidate.equals("class")) {
          candidate = validator.validateName("clazz", true);
        }
        if (!possibleNames.contains(candidate) && GroovyNamesUtil.isIdentifier(candidate)) {
          possibleNames.add(candidate);
        }
      }
    }
  }

  private static String generateNameForBuiltInType(String unboxed) {
    return StringUtil.toLowerCase(unboxed).substring(0, 1);
  }

  private static String fromUpperLetter(String str) {
    if (str.isEmpty()) return "";
    if (str.length() == 1) return StringUtil.toUpperCase(str);
    return StringUtil.toUpperCase(str.substring(0, 1)) + str.substring(1);
  }

  @Nullable
  private static PsiType getCollectionComponentType(PsiType type, Project project) {
    if (!(type instanceof PsiClassType)) return null;
    PsiClassType classType = (PsiClassType) type;
    PsiClassType.ClassResolveResult result = classType.resolveGenerics();
    PsiClass clazz = result.getElement();
    if (clazz == null) return null;
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    @SuppressWarnings({"ConstantConditions"}) PsiClass collectionClass = facade.findClass("java.util.Collection", type.getResolveScope());
    if (collectionClass == null || collectionClass.getTypeParameters().length != 1) return null;
    PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(collectionClass, clazz, result.getSubstitutor());

    if (substitutor == null) return null;
    PsiType componentType = substitutor.substitute(collectionClass.getTypeParameters()[0]);
    return componentType instanceof PsiIntersectionType ? null : componentType;
  }
}
