/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSuperReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrThisReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.refactoring.introduceVariable.GroovyIntroduceVariableBase;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author ilyas
 */
public class GroovyNameSuggestionUtil {

  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil");

  public static String[] suggestVariableNames(GrExpression expr,
                                              GroovyIntroduceVariableBase.Validator validator) {
    ArrayList<String> possibleNames = new ArrayList<String>();
    PsiType type = expr.getType();
    if (type != null && !PsiType.VOID.equals(type)) {
      String unboxed = PsiTypesUtil.unboxIfPossible(type.getCanonicalText());
      if (unboxed != null && !unboxed.equals(type.getCanonicalText())) {
        String name = generateNameForBuiltInType(unboxed);
        name = validator.validateName(name, true);
        if (GroovyNamesUtil.isIdentifier(name)) {
          possibleNames.add(name);
        }
      } else {
        generateByType(expr.getType(), possibleNames, validator);
      }
    }
    generateNameByExpr(expr, possibleNames, validator);
    while (possibleNames.contains("")) {
      possibleNames.remove("");
    }
    if (possibleNames.size() == 0) {
      possibleNames.add(validator.validateName("var", true));
    }
    return possibleNames.toArray(new String[possibleNames.size()]);
  }


  private static void generateNameByExpr(GrExpression expr, ArrayList<String> possibleNames, GroovyIntroduceVariableBase.Validator validator) {
    if (expr instanceof GrThisReferenceExpression) {
      possibleNames.add(validator.validateName("thisInstance", true));
    }
    if (expr instanceof GrSuperReferenceExpression) {
      possibleNames.add(validator.validateName("superInstance", true));
    }
    if (expr instanceof GrReferenceExpression && ((GrReferenceExpression) expr).getName() != null) {
      GrReferenceExpression refExpr = (GrReferenceExpression) expr;
      if (refExpr.getName().toUpperCase().equals(refExpr.getName())) {
        possibleNames.add(validator.validateName(refExpr.getName().toLowerCase(), true));
      } else {
        generateCamelNames(possibleNames, validator, refExpr.getName());
      }
      if (expr.getText().equals(refExpr.getName())) {
        possibleNames.remove(refExpr.getName());
      }
    }
    if (expr instanceof GrMethodCallExpression) {
      generateNameByExpr(((GrMethodCallExpression) expr).getInvokedExpression(), possibleNames, validator);
    }
  }

  private static void generateByType(PsiType type, ArrayList<String> possibleNames, GroovyIntroduceVariableBase.Validator validator) {
    String typeName = type.getPresentableText();
    generateNamesForColletionType(type, possibleNames, validator);
    generateNamesForArrayType(type, possibleNames, validator);
    typeName = cleanTypeName(typeName);
    if (typeName.equals("String")) {
      possibleNames.add(validator.validateName("s", true));
    }
    if (typeName.equals("Closure")) {
      possibleNames.add(validator.validateName("cl", true));
    }
    if (typeName.toUpperCase().equals(typeName)) {
      possibleNames.add(validator.validateName(deleteNonLetterFromString(typeName.toLowerCase()), true));
    } else if (!typeName.equals(typeName.toLowerCase())) {
      generateCamelNames(possibleNames, validator, typeName);
      possibleNames.remove(typeName);
    }
  }

  private static void generateNamesForArrayType(PsiType type, ArrayList<String> possibleNames, GroovyIntroduceVariableBase.Validator validator) {
/*
    int arrayDim = type.getArrayDimensions();
    if (arrayDim != 1) return;
*/
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

  private static void generateNamesForColletionType(PsiType type, ArrayList<String> possibleNames, GroovyIntroduceVariableBase.Validator validator) {
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
    String candidate = fromLowerLetter(componentName.toLowerCase()) + "s";
    possibleNames.add(validator.validateName(candidate, true));
    candidate = collectionName.toLowerCase() + "Of" + fromUpperLetter(componentName.toLowerCase()) + "s";
    possibleNames.add(validator.validateName(candidate, true));
  }

  private static void generateCamelNames(ArrayList<String> possibleNames, GroovyIntroduceVariableBase.Validator validator, String typeName) {
    ArrayList<String> camelTokens = camelizeString(typeName);
    Collections.reverse(camelTokens);
    if (camelTokens.size() > 0) {
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
    return unboxed.toLowerCase().substring(0, 1);
  }

  private static ArrayList<String> camelizeString(String str) {
    String tempString = str;
    tempString = deleteNonLetterFromString(tempString);
    ArrayList<String> camelizedTokens = new ArrayList<String>();
    if (!GroovyNamesUtil.isIdentifier(tempString)) {
      return camelizedTokens;
    }
    String result = fromLowerLetter(tempString);
    while (!result.equals("")) {
      result = fromLowerLetter(result);
      String temp = "";
      while (!(result.length() == 0) && !result.substring(0, 1).toUpperCase().equals(result.substring(0, 1))) {
        temp += result.substring(0, 1);
        result = result.substring(1);
      }
      camelizedTokens.add(temp);
    }
    return camelizedTokens;
  }

  private static String deleteNonLetterFromString(String tempString) {
    Pattern pattern = Pattern.compile("[^a-zA-Z]");
    Matcher matcher = pattern.matcher(tempString);
    return matcher.replaceAll("");
  }

  private static String fromLowerLetter(String str) {
    if (str.length() == 0) return "";
    if (str.length() == 1) return str.toLowerCase();
    return str.substring(0, 1).toLowerCase() + str.substring(1);
  }

  private static String fromUpperLetter(String str) {
    if (str.length() == 0) return "";
    if (str.length() == 1) return str.toUpperCase();
    return str.substring(0, 1).toUpperCase() + str.substring(1);
  }

  private static PsiType getCollectionComponentType(PsiType type, Project project) {
    if (!(type instanceof PsiClassType)) return null;
    PsiClassType classType = (PsiClassType) type;
    PsiClassType.ClassResolveResult result = classType.resolveGenerics();
    PsiClass clazz = result.getElement();
    if (clazz == null) return null;
    PsiManager manager = PsiManager.getInstance(project);
    PsiClass collectionClass = manager.findClass("java.util.Collection", ((PsiClassType) type).getResolveScope());
    if (collectionClass == null || collectionClass.getTypeParameters().length != 1) return null;
    PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(collectionClass, clazz, result.getSubstitutor());

    if (substitutor == null) return null;
    PsiType componentType = substitutor.substitute(collectionClass.getTypeParameters()[0]);
    return componentType instanceof PsiIntersectionType ? null : componentType;
  }
}
