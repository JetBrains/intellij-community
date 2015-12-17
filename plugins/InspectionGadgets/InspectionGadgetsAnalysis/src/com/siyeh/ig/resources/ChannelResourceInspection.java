/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class ChannelResourceInspection extends ResourceInspection {

  @Override
  @NotNull
  public String getID() {
    return "ChannelOpenedButNotSafelyClosed";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("channel.opened.not.closed.display.name");
  }

  @Override
  protected boolean isResourceCreation(PsiExpression expression) {
    if (!(expression instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final String methodName = methodExpression.getReferenceName();
    if (!HardcodedMethodConstants.GET_CHANNEL.equals(methodName)) {
      return false;
    }
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier == null ||
        TypeUtils.expressionHasTypeOrSubtype(qualifier,
                                             "java.net.Socket", "java.net.DatagramSocket", "java.net.ServerSocket",
                                             "java.net.SocketInputStream", "java.net.SocketOutputStream", "java.io.FileInputStream",
                                             "java.io.FileOutputStream", "java.io.RandomAccessFile",
                                             "com.sun.corba.se.pept.transport.EventHandler", "sun.nio.ch.InheritedChannel") == null) {
      return false;
    }
    return TypeUtils.expressionHasTypeOrSubtype(expression, "java.nio.channels.Channel");
  }

  @Override
  protected boolean isResourceFactoryClosed(PsiExpression expression) {
    if (!(expression instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (!(qualifier instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
    final PsiElement target = referenceExpression.resolve();
    if (!(target instanceof PsiVariable)) {
      return false;
    }
    final PsiVariable variable = (PsiVariable)target;
    PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(expression, PsiTryStatement.class, true, PsiMember.class);
    if (tryStatement == null) {
      return false;
    }
    while (!isResourceClosedInFinally(tryStatement, variable)) {
      tryStatement = PsiTreeUtil.getParentOfType(tryStatement, PsiTryStatement.class, true, PsiMember.class);
      if (tryStatement == null) {
        return false;
      }
    }
    return true;
  }
}
