/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiSynchronizedStatement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class SynchronizeOnLockInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "SynchroniziationOnLockObject";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "synchronize.on.lock.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final String type = (String)infos[0];
    return InspectionGadgetsBundle.message(
      "synchronize.on.lock.problem.descriptor", type);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SynchronizeOnLockVisitor();
  }

  private static class SynchronizeOnLockVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitSynchronizedStatement(
      @NotNull PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      final PsiExpression lockExpression = statement.getLockExpression();
      if (lockExpression == null) {
        return;
      }
      final String type = TypeUtils.expressionHasTypeOrSubtype(
        lockExpression,
        "java.util.concurrent.locks.Lock",
        "java.util.concurrent.locks.ReadWriteLock");
      if (type == null) {
        return;
      }
      registerError(lockExpression, type);
    }
  }
}