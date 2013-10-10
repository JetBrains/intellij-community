/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.maturity;

import com.intellij.psi.PsiComment;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class TodoCommentInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("todo.comment.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("todo.comment.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassWithoutToStringVisitor();
  }

  private static class ClassWithoutToStringVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitComment(PsiComment comment) {
      super.visitComment(comment);
      if (TodoUtil.isTodoComment(comment)) {
        registerError(comment);
      }
    }
  }
}