/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiSuperMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.stream.Stream;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_GSTRING;

public class GroovyGStringKeyInspection extends BaseInspection {
  private static final String PUT_METHOD = "put";

  @Override
  @Nullable
  protected String buildErrorString(Object... args) {
    return "GString is used as map's key #loc";

  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor
  {
    @Override
    public void visitListOrMap(@NotNull GrListOrMap expression) {
      if (!expression.isMap()) {
        return;
      }

      Stream.of(expression.getNamedArguments()).forEach(this::checkArgument);
    }

    @Override
    public void visitMethodCallExpression(@NotNull GrMethodCallExpression methodCallExpression) {
      checkMethodCall(methodCallExpression);
    }

    @Override
    public void visitApplicationStatement(@NotNull GrApplicationStatement applicationStatement) {
      checkMethodCall(applicationStatement);
    }

    public void checkMethodCall(@NotNull GrMethodCall grMethodCall) {
      final GrArgumentList args = grMethodCall.getArgumentList();

      if (args.getExpressionArguments().length != 2 || args.getAllArguments().length != 2) {
        return;
      }

      final GrExpression firstArgument = args.getExpressionArguments()[0];
      if (!isGStringType(firstArgument) ) {
        return;
      }

      if (!isMapPutMethod(grMethodCall)) return;

      registerError(firstArgument);
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

    private void checkArgument(@NotNull GrNamedArgument argument) {
      final GrArgumentLabel argumentLabel = argument.getLabel();
      if (argumentLabel == null) return;
      final GrExpression labelExpression = argumentLabel.getExpression();
      if (labelExpression == null) return;
      if (isGStringType(labelExpression)) {
        registerError(argumentLabel);
      }
    }

    private static boolean isGStringType(@NotNull GrExpression expression) {
      PsiType expressionType = expression.getType();
      if (expressionType == null) {
        return false;
      }
      PsiClassType type = TypesUtil.createTypeByFQClassName(GROOVY_LANG_GSTRING, expression);
      return type.isAssignableFrom(expressionType);
    }
  }
}