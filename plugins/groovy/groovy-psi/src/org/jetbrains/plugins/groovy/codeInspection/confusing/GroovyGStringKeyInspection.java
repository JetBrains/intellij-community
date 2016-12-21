/*
 * Copyright 2007-2008 Dave Griffith
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
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.stream.Stream;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_GSTRING;

public class GroovyGStringKeyInspection extends BaseInspection {
  private static final String PUT_METHOD = "put";

  @Override
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return CONFUSING_CODE_CONSTRUCTS;
  }

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
    public void visitExpression(@NotNull GrExpression expression) {
      if (expression instanceof GrListOrMap) {
        checkMapLiteral((GrListOrMap)expression);
      }
    }

    @Override
    public void visitMethodCallExpression(@NotNull GrMethodCallExpression grMethodCallExpression) {
      final GrArgumentList args = grMethodCallExpression.getArgumentList();
      if (args.getExpressionArguments().length != 2 || PsiImplUtil.hasNamedArguments(args)) {
        return;
      }
      if (!isMapPutMethod(grMethodCallExpression)) return;

      GrExpression firstArgument = grMethodCallExpression.getArgumentList().getExpressionArguments()[0];
      if (isType(firstArgument, GROOVY_LANG_GSTRING) ) {
        registerError(firstArgument);
      }
    }

    boolean isMapPutMethod(@NotNull GrMethodCallExpression grMethodCallExpression) {
      GrExpression methodExpression = grMethodCallExpression.getInvokedExpression();
      if (!(methodExpression instanceof GrReferenceExpression)) {
        return false;
      }
      final GrReferenceExpression referenceExpression = (GrReferenceExpression) methodExpression;

      if (!PUT_METHOD.equals(referenceExpression.getReferenceName())) return false;
      final PsiMethod method = grMethodCallExpression.resolveMethod();
      if (method == null) return false;
      PsiMethod[] superMethods = method.findDeepestSuperMethods();
      if (superMethods.length == 0) {
        superMethods = new PsiMethod[]{method};
      }
      return StreamEx.of(superMethods).map(PsiMember::getContainingClass).nonNull().map(PsiClass::getQualifiedName)
               .has(JAVA_UTIL_MAP);
    }

    private void checkMapLiteral(@NotNull GrListOrMap expression) {
      if (!expression.isMap()) {
        return;
      }

      Stream.of(expression.getNamedArguments()).forEach(this::checkArgument);
    }

    private void checkArgument(@NotNull GrNamedArgument argument) {
      final GrArgumentLabel argumentLabel = argument.getLabel();
      if (argumentLabel == null) return;
      final GrExpression labelExpression = argumentLabel.getExpression();
      if (labelExpression == null) return;
      if (isType(labelExpression, GROOVY_LANG_GSTRING)) {
        registerError(argumentLabel);
      }
    }

    private static boolean isType(GrExpression expression, String typeName) {
      PsiClassType type = TypesUtil.createTypeByFQClassName(typeName, expression);
      PsiType expressionType = expression.getType();
      if (expressionType == null) {
        return false;
      }
      return type.isAssignableFrom(expressionType);
    }
  }
}