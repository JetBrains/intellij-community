/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.threading;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

public class SynchronizeOnLockInspection extends MethodInspection {

  public String getID() {
    return "SynchroniziationOnLockObject";
  }

  public String getGroupDisplayName() {
    return GroupNames.THREADING_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new SynchronizeOnLockVisitor();
  }

  private static class SynchronizeOnLockVisitor extends BaseInspectionVisitor {


    public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      final PsiExpression lockExpression = statement.getLockExpression();
      if (lockExpression == null) {
        return;
      }
      final PsiType type = lockExpression.getType();
      if (type == null) {
        return;
      }
      final PsiManager manager = lockExpression.getManager();
      final Project project = manager.getProject();
      final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
      final PsiClass javaUtilLockClass =
        manager.findClass("java.util.concurrent.locks.Lock", scope);
      if (javaUtilLockClass == null) {
        return;
      }
      final PsiElementFactory elementFactory =
        manager.getElementFactory();
      final PsiClassType javaUtilLockType =
        elementFactory.createType(javaUtilLockClass);
      if (!javaUtilLockType.isAssignableFrom(type)) {
        return;
      }
      registerError(lockExpression);
    }
  }
}
