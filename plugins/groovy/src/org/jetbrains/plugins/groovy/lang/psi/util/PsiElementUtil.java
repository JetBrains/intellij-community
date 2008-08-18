/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiElement;

/**
 * @author ilyas
 */
public class PsiElementUtil {
  public static final String GETTER_PREFIX = "get";
  public static final String SETTER_PREFIX = "set";

  public static boolean isPropertyAccessor(GrCall call) {
    return isGetterInvocation(call) || isSetterInvocation(call);
  }

  public static boolean isSetterInvocation(GrCall call) {
    if (!(call instanceof GrMethodCallExpression) &&
        !(call instanceof GrApplicationStatement)) {
      return false;
    }

    GrExpression expr = (call instanceof GrMethodCallExpression) ?
        ((GrMethodCallExpression) call).getInvokedExpression() :
        ((GrApplicationStatement) call).getFunExpression();

    if (!(expr instanceof GrReferenceExpression)) return false;

    GrReferenceExpression refExpr = (GrReferenceExpression) expr;
    String name = refExpr.getName();
    if (name == null || !name.startsWith(SETTER_PREFIX)) return false;

    name = name.substring(SETTER_PREFIX.length());
    String propName = StringUtil.decapitalize(name);
    if (propName.length() == 0 || name.equals(propName)) return false;

    if (call instanceof GrApplicationStatement) {
      PsiElement element = refExpr.resolve();
      if (!(element instanceof PsiMethod) || !PsiUtil.isSimplePropertySetter(((PsiMethod) element))) return false;
    } else {
      PsiMethod method = ((GrMethodCallExpression) call).resolveMethod();
      if (!PsiUtil.isSimplePropertySetter(method)) return false;
    }

    if (call instanceof GrMethodCallExpression) {
      GrArgumentList args = ((GrMethodCallExpression) call).getArgumentList();
      return args != null &&
          args.getExpressionArguments().length == 1 &&
          args.getNamedArguments().length == 0;
    }

    GrArgumentList args = call.getArgumentList();
    return args != null &&
        args.getExpressionArguments().length == 1 &&
        args.getNamedArguments().length == 0;

  }

  public static boolean isGetterInvocation(GrCall call) {
    if (!(call instanceof GrMethodCallExpression)) return false;
    GrMethodCallExpression methodCall = (GrMethodCallExpression) call;
    GrExpression expr = methodCall.getInvokedExpression();
    if (!(expr instanceof GrReferenceExpression)) return false;

    GrReferenceExpression refExpr = (GrReferenceExpression) expr;
    String name = refExpr.getName();
    if (name == null || !name.startsWith(GETTER_PREFIX)) return false;

    name = name.substring(GETTER_PREFIX.length());
    String propName = StringUtil.decapitalize(name);
    if (propName.length() == 0 || name.equals(propName)) return false;


    PsiMethod method = methodCall.resolveMethod();
    if (!PsiUtil.isSimplePropertyGetter(method)) return false;

    GrArgumentList args = methodCall.getArgumentList();
    return args != null && args.getExpressionArguments().length == 0;
  }


}
