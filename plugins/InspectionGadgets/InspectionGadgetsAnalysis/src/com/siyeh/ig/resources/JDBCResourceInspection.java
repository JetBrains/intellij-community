/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class JDBCResourceInspection extends ResourceInspection {

  private static final String[] creationMethodClassName =
    {
      "java.sql.Driver",
      "java.sql.DriverManager",
      "javax.sql.DataSource",
      "java.sql.Connection",
      "java.sql.Connection",
      "java.sql.Connection",
      "java.sql.Statement",
      "java.sql.Statement",
      "java.sql.Statement",
    };
  @NonNls
  private static final String[] creationMethodName =
    {
      "connect",
      "getConnection",
      "getConnection",
      "createStatement",
      "prepareStatement",
      "prepareCall",
      "executeQuery",
      "getResultSet",
      "getGeneratedKeys"
    };

  @SuppressWarnings({"StaticCollection"})
  private static final Set<String> creationMethodNameSet = new HashSet<>(9);

  static {
    ContainerUtil.addAll(creationMethodNameSet, creationMethodName);
  }

  @Override
  @NotNull
  public String getID() {
    return "JDBCResourceOpenedButNotSafelyClosed";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("jdbc.resource.opened.not.closed.display.name");
  }

  protected boolean isResourceCreation(PsiExpression expression) {
    if (!(expression instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final String name = methodExpression.getReferenceName();
    if (name == null || !creationMethodNameSet.contains(name)) {
      return false;
    }
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) {
      return false;
    }
    for (int i = 0; i < creationMethodName.length; i++) {
      if (!name.equals(creationMethodName[i])) {
        continue;
      }
      final PsiClass containingClass = method.getContainingClass();
      final String expectedClassName = creationMethodClassName[i];
      if (InheritanceUtil.isInheritor(containingClass, false, expectedClassName)) {
        return true;
      }
    }
    return false;
  }
}
