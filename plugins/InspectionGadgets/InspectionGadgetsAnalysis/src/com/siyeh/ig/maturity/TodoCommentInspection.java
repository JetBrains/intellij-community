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

import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TodoCommentInspection extends BaseJavaBatchLocalInspectionTool {

  @Nullable
  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final PsiTodoSearchHelper searchHelper = PsiTodoSearchHelper.SERVICE.getInstance(file.getProject());
    final TodoItem[] todoItems = searchHelper.findTodoItems(file);

    final List<ProblemDescriptor> result = new ArrayList<ProblemDescriptor>();
    for (TodoItem todoItem : todoItems) {
      final PsiComment comment = PsiTreeUtil.getParentOfType(file.findElementAt(todoItem.getTextRange().getStartOffset()), PsiComment.class, false);
      if (comment != null) {
        result.add(manager.createProblemDescriptor(comment, InspectionGadgetsBundle.message("todo.comment.problem.descriptor"), isOnTheFly,
                                                   null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
      }
    }
    return result.toArray(new ProblemDescriptor[result.size()]);
  }
}