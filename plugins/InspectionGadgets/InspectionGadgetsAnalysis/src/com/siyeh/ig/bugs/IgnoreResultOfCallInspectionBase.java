/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.LibraryUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class IgnoreResultOfCallInspectionBase extends BaseInspection {

  final List<String> methodNamePatterns = new ArrayList();
  final List<String> classNames = new ArrayList();
  /**
   * @noinspection PublicField
   */
  public boolean m_reportAllNonLibraryCalls = false;
  /**
   * @noinspection PublicField
   */
  @NonNls public String callCheckString = "java.io.File,.*," +
                                          "java.io.InputStream,read|skip|available|markSupported," +
                                          "java.io.Writer,read|skip|ready|markSupported," +
                                          "java.lang.Boolean,.*," +
                                          "java.lang.Byte,.*," +
                                          "java.lang.Character,.*," +
                                          "java.lang.Double,.*," +
                                          "java.lang.Float,.*," +
                                          "java.lang.Integer,.*," +
                                          "java.lang.Long,.*," +
                                          "java.lang.Math,.*," +
                                          "java.lang.Object,equals|hashCode|toString," +
                                          "java.lang.Short,.*," +
                                          "java.lang.StrictMath,.*," +
                                          "java.lang.String,.*," +
                                          "java.math.BigInteger,.*," +
                                          "java.math.BigDecimal,.*," +
                                          "java.net.InetAddress,.*," +
                                          "java.net.URI,.*," +
                                          "java.util.UUID,.*," +
                                          "java.util.regex.Matcher,pattern|toMatchResult|start|end|group|groupCount|matches|find|lookingAt|quoteReplacement|replaceAll|replaceFirst|regionStart|regionEnd|hasTransparantBounds|hasAnchoringBounds|hitEnd|requireEnd," +
                                          "java.util.regex.Pattern,.*";
  Map<String, Pattern> patternCache = null;

  public IgnoreResultOfCallInspectionBase() {
    parseString(callCheckString, classNames, methodNamePatterns);
  }

  @Override
  @NotNull
  public String getID() {
    return "ResultOfMethodCallIgnored";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("result.of.method.call.ignored.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiClass containingClass = (PsiClass)infos[0];
    final String className = containingClass.getName();
    return InspectionGadgetsBundle.message("result.of.method.call.ignored.problem.descriptor", className);
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(callCheckString, classNames, methodNamePatterns);
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    callCheckString = formatString(classNames, methodNamePatterns);
    super.writeSettings(element);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IgnoreResultOfCallVisitor();
  }

  private class IgnoreResultOfCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitExpressionStatement(@NotNull PsiExpressionStatement statement) {
      super.visitExpressionStatement(statement);
      final PsiExpression expression = statement.getExpression();
      if (!(expression instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      final PsiMethod method = call.resolveMethod();
      if (method == null || method.isConstructor()) {
        return;
      }
      final PsiType returnType = method.getReturnType();
      if (PsiType.VOID.equals(returnType)) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      if (PsiUtilCore.hasErrorElementChild(statement)) {
        return;
      }
      if (m_reportAllNonLibraryCalls && !LibraryUtil.classIsInLibrary(aClass)) {
        registerMethodCallError(call, aClass);
        return;
      }
      
      PsiAnnotation contractAnnotation = ControlFlowAnalyzer.findContractAnnotation(method);
      if (contractAnnotation != null && Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(contractAnnotation, "pure"))) {
        registerMethodCallError(call, aClass);
        return;
      }

      final PsiReferenceExpression methodExpression = call.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (methodName == null) {
        return;
      }
      for (int i = 0; i < methodNamePatterns.size(); i++) {
        final String methodNamePattern = methodNamePatterns.get(i);
        if (!methodNamesMatch(methodName, methodNamePattern)) {
          continue;
        }
        final String className = classNames.get(i);
        if (!InheritanceUtil.isInheritor(aClass, className)) {
          continue;
        }
        registerMethodCallError(call, aClass);
        return;
      }
    }

    private boolean methodNamesMatch(String methodName, String methodNamePattern) {
      Pattern pattern;
      if (patternCache != null) {
        pattern = patternCache.get(methodNamePattern);
      }
      else {
        patternCache = new HashMap(methodNamePatterns.size());
        pattern = null;
      }
      if (pattern == null) {
        try {
          pattern = Pattern.compile(methodNamePattern);
          patternCache.put(methodNamePattern, pattern);
        }
        catch (PatternSyntaxException ignore) {
          return false;
        }
        catch (NullPointerException ignore) {
          return false;
        }
      }
      if (pattern == null) {
        return false;
      }
      final Matcher matcher = pattern.matcher(methodName);
      return matcher.matches();
    }
  }
}
