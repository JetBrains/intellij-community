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
package com.siyeh.ig.errorhandling;

import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.util.FileTypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class EmptyTryBlockInspection extends BaseInspection {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "empty.try.block.problem.descriptor");
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return !FileTypeUtils.isInServerPageFile(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EmptyTryBlockVisitor();
  }

  private static class EmptyTryBlockVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitTryStatement(@NotNull PsiTryStatement statement) {
      super.visitTryStatement(statement);
      final PsiCodeBlock tryBlock = statement.getTryBlock();
      if (tryBlock == null) {
        return;
      }
      if (!tryBlock.isEmpty()) {
        return;
      }
      registerStatementError(statement);
    }
  }
}