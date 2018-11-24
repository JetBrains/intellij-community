/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.PsiTodoSearchHelper;

import java.util.List;

public class TodoForExistingFile extends TodoForRanges {
  private final VirtualFile myFile;

  public TodoForExistingFile(Project project,
                              List<TextRange> ranges,
                              int additionalOffset,
                              String name,
                              String text,
                              boolean revision, FileType type, VirtualFile file) {
    super(project, ranges, additionalOffset, name, text, revision, type);
    myFile = file;
  }

  protected TodoItemData[] getTodoItems() {
    return ReadAction.compute(() -> {
      final PsiTodoSearchHelper helper = PsiTodoSearchHelper.SERVICE.getInstance(myProject);

      PsiFile psiFile = myFile == null ? null : PsiManager.getInstance(myProject).findFile(myFile);
      if (psiFile != null) {
        return TodoForBaseRevision.convertTodo(helper.findTodoItems(psiFile));
      }

      return TodoForBaseRevision.convertTodo(getTodoForText(helper));
    });
  }
}
