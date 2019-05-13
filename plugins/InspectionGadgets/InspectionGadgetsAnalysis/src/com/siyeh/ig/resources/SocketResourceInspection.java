/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.resources;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SocketResourceInspection extends ResourceInspection {

  @Override
  @NotNull
  public String getID() {
    return "SocketOpenedButNotSafelyClosed";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("socket.opened.not.closed.display.name");
  }

  @Override
  protected boolean isResourceCreation(PsiExpression expression) {
    if (expression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"accept".equals(methodName)) {
        return false;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null || !TypeUtils.expressionHasTypeOrSubtype(qualifier, "java.net.ServerSocket")) {
        return false;
      }
      return TypeUtils.expressionHasTypeOrSubtype(methodCallExpression, "java.net.Socket");
    }
    else if (expression instanceof PsiNewExpression) {
      return TypeUtils.expressionHasTypeOrSubtype(expression, "java.net.Socket", "java.net.DatagramSocket", "java.net.ServerSocket") != null;
    }
    return false;
  }
}
