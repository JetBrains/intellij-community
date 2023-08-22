// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiSuperMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_GSTRING;

public class GroovyGStringKeyInspection extends BaseInspection {
  @NlsSafe private static final String PUT_METHOD = "put";

  @Override
  @Nullable
  protected String buildErrorString(Object... args) {
    return GroovyBundle.message("inspection.message.gstring.used.as.maps.key");
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor
  {
    @Override
    public void visitNamedArgument(@NotNull GrNamedArgument namedArgument) {
      PsiElement parent = namedArgument.getParent();
      if (!(parent instanceof GrListOrMap) || !((GrListOrMap)parent).isMap()) return;

      final GrArgumentLabel argumentLabel = namedArgument.getLabel();
      if (argumentLabel == null) return;
      final GrExpression labelExpression = argumentLabel.getExpression();
      if (labelExpression == null) return;
      if (isGStringType(labelExpression)) {
        registerError(argumentLabel);
      }
    }

    @Override
    public void visitExpression(@NotNull GrExpression grExpression) {
      final PsiElement gstringParent = grExpression.getParent();
      if (!(gstringParent instanceof GrArgumentList)) return;

      GrExpression[] arguments = ((GrArgumentList)gstringParent).getExpressionArguments();
      if (arguments.length != 2 || !arguments[0].equals(grExpression)) return;

      final PsiElement grandparent = gstringParent.getParent();
      if (!(grandparent instanceof GrMethodCall)) {
        return;
      }

      if (!isGStringType(grExpression)) return;
      if (!isMapPutMethod((GrMethodCall)grandparent)) return;

      registerError(grExpression);
    }

    boolean isMapPutMethod(@NotNull GrMethodCall grMethodCall) {
      final PsiMethod method = grMethodCall.resolveMethod();
      if (method == null) return false;

      if (!PUT_METHOD.equals(method.getName())) return false;

      PsiClass mapClass = JavaPsiFacade.getInstance(grMethodCall.getProject()).findClass(JAVA_UTIL_MAP, grMethodCall.getResolveScope());
      if (mapClass == null) return false;
      PsiMethod[] methods = mapClass.findMethodsByName(PUT_METHOD, false);
      for (PsiMethod superMethod : methods) {
        if (superMethod.equals(method) || PsiSuperMethodUtil.isSuperMethod(method, superMethod))
          return true;
      }
      return false;
    }

    private static boolean isGStringType(@NotNull GrExpression expression) {
      PsiType expressionType = expression.getType();
      return expressionType != null && expressionType.equalsToText(GROOVY_LANG_GSTRING);
    }
  }
}