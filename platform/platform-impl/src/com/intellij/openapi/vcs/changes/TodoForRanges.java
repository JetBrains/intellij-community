/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.checkin.StepIntersection;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.util.containers.Convertor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 9/9/11
 * Time: 9:55 AM
 */
public abstract class TodoForRanges {
  protected final Project myProject;
  private final List<TextRange> myRanges;
  private final int myAdditionalOffset;
  protected final String myFileName;
  protected final String myText;
  protected final boolean myOldRevision;
  protected final FileType myFileType;

  protected TodoForRanges(final Project project,
                       final List<TextRange> ranges,
                       int additionalOffset,
                       String name,
                       String text,
                       boolean revision, FileType type) {
    myProject = project;
    myRanges = ranges;
    myAdditionalOffset = additionalOffset;
    myFileName = name;
    myText = text;
    myOldRevision = revision;
    myFileType = type;
  }

  public List<Pair<TextRange, TextAttributes>> execute() {
    final TodoItemData[] todoItems = getTodoItems();
    
    final StepIntersection<TodoItemData, TextRange> stepIntersection =
      new StepIntersection<>(new Convertor<TodoItemData, TextRange>() {
        @Override
        public TextRange convert(TodoItemData o) {
          return o.getTextRange();
        }
      }, Convertor.SELF, myRanges, () -> ""
      );
    final List<TodoItemData> filtered = stepIntersection.process(Arrays.asList(todoItems));
    final List<Pair<TextRange, TextAttributes>> result = new ArrayList<>(filtered.size());
    int offset = 0;
    for (TextRange range : myRanges) {
      Iterator<TodoItemData> iterator = filtered.iterator();
      while (iterator.hasNext()) {
        TodoItemData item = iterator.next();
        if (range.contains(item.getTextRange())) {
          TextRange todoRange = new TextRange(offset - range.getStartOffset() + item.getTextRange().getStartOffset(),
                                              offset - range.getStartOffset() + item.getTextRange().getEndOffset());
          result.add(Pair.create(todoRange, item.getPattern().getAttributes().getTextAttributes()));
          iterator.remove();
        } else {
          break;
        }
      }
      offset += range.getLength() + 1 + myAdditionalOffset;
    }
    return result;
  }

  protected abstract TodoItemData[] getTodoItems();

  protected TodoItem[] getTodoForText(PsiTodoSearchHelper helper) {
    final PsiFile psiFile = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
      @Override
      public PsiFile compute() {
        return PsiFileFactory.getInstance(myProject).createFileFromText((myOldRevision ? "old" : "") + myFileName, myFileType, myText);
      }
    });
    return helper.findTodoItemsLight(psiFile);
  }
}
