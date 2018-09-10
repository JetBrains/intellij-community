/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.internationalization;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.*;

/**
 * @author Bas Leijdekkers
 */
public class ImplicitDefaultCharsetUsageInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("implicit.default.charset.usage.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    if (infos[0] instanceof PsiNewExpression) {
      return InspectionGadgetsBundle.message("implicit.default.charset.usage.constructor.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("implicit.default.charset.usage.problem.descriptor");
    }
  }

  private static final Key<Boolean> HAS_CHARSET_OVERLOAD = Key.create("Method has Charset overload");

  @Contract("null -> false")
  private static boolean hasCharsetOverload(PsiMethod method) {
    if (method == null) return false;
    Boolean hasCharsetOverload = method.getUserData(HAS_CHARSET_OVERLOAD);
    if (hasCharsetOverload == null) {
      PsiMethod methodWithCharsetArgument = null;
      PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
        PsiType charsetType =
          JavaPsiFacade.getElementFactory(method.getProject()).createTypeByFQClassName("java.nio.charset.Charset", method.getResolveScope());
        MethodSignature newSignature = MethodSignatureUtil
          .createMethodSignature(signature.getName(), ArrayUtil.append(signature.getParameterTypes(), charsetType),
                                 signature.getTypeParameters(), signature.getSubstitutor(), signature.isConstructor()
          );
        methodWithCharsetArgument = MethodSignatureUtil.findMethodBySignature(aClass, newSignature, false);
      }
      hasCharsetOverload = methodWithCharsetArgument != null;
      method.putUserData(HAS_CHARSET_OVERLOAD, hasCharsetOverload);
    }
    return hasCharsetOverload;
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    PsiCallExpression call = (PsiCallExpression)infos[0];
    if (!PsiUtil.isLanguageLevel7OrHigher(call)) return null;
    PsiMethod method = call.resolveMethod();
    return hasCharsetOverload(method) ? new AddUtf8CharsetFix() : null;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ImplicitDefaultCharsetUsageVisitor();
  }

  private static class ImplicitDefaultCharsetUsageVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String name = methodExpression.getReferenceName();
      if (!"getBytes".equals(name)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() == 1) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass ==  null) {
        return;
      }
      final String qName = aClass.getQualifiedName();
      if (!CommonClassNames.JAVA_LANG_STRING.equals(qName)) {
        return;
      }
      registerMethodCallError(expression, expression);
    }

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiMethod constructor = expression.resolveConstructor();
      if (constructor == null) {
        return;
      }
      final PsiClass aClass = constructor.getContainingClass();
      if (aClass == null) {
        return;
      }
      final PsiParameterList parameterList = constructor.getParameterList();
      final int count = parameterList.getParametersCount();
      if (count == 0) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      final String qName = aClass.getQualifiedName();
      if (CommonClassNames.JAVA_LANG_STRING.equals(qName)) {
        if (!parameters[0].getType().equalsToText("byte[]") || hasCharsetType(parameters[count - 1])) {
          return;
        }
      }
      else if ("java.io.InputStreamReader".equals(qName) ||
               "java.io.OutputStreamWriter".equals(qName) ||
               "java.io.PrintStream".equals(qName)) {
        if (hasCharsetType(parameters[count - 1])) {
          return;
        }
      }
      else if ("java.io.PrintWriter".equals(qName)) {
        if (count > 1 && hasCharsetType(parameters[count - 1]) || parameters[0].getType().equalsToText("java.io.Writer")) {
          return;
        }
      }
      else if ("java.util.Formatter".equals(qName)) {
        if (count > 1 && hasCharsetType(parameters[1])) {
          return;
        }
        final PsiType firstType = parameters[0].getType();
        if (!firstType.equalsToText(CommonClassNames.JAVA_LANG_STRING) && !firstType.equalsToText("java.io.File") &&
          !firstType.equalsToText("java.io.OutputStream")) {
          return;
        }
      }
      else if ("java.util.Scanner".equals(qName)) {
        if (count > 1 && hasCharsetType(parameters[1])) {
          return;
        }
        final PsiType firstType = parameters[0].getType();
        if (!firstType.equalsToText("java.io.InputStream") && !firstType.equalsToText("java.io.File") &&
          !firstType.equalsToText("java.nio.file.Path") && !firstType.equalsToText("java.nio.channels.ReadableByteChannel")) {
          return;
        }
      }
      else if (!"java.io.FileReader".equals(qName) && !"java.io.FileWriter".equals(qName)) {
        return;
      }
      registerNewExpressionError(expression, expression);

    }

    private static boolean hasCharsetType(PsiVariable variable) {
      return TypeUtils.variableHasTypeOrSubtype(variable, CommonClassNames.JAVA_LANG_STRING,
                                                "java.nio.charset.Charset",
                                                "java.nio.charset.CharsetEncoder",
                                                "java.nio.charset.CharsetDecoder");
    }
  }

  private static class AddUtf8CharsetFix extends InspectionGadgetsFix {
    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      PsiCallExpression call = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiCallExpression.class);
      if (call == null) return;
      PsiExpressionList arguments = call.getArgumentList();
      if (arguments == null) return;
      PsiExpression charsetArg =
        JavaPsiFacade.getElementFactory(project).createExpressionFromText("java.nio.charset.StandardCharsets.UTF_8", call);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(arguments.add(charsetArg));
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("implicit.default.charset.usage.fix.family.name");
    }
  }
}
