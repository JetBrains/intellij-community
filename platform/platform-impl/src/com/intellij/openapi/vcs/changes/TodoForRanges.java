/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
public class TodoForRanges {
  private final Project myProject;
  private final String myFileName;
  private final String myText;
  private final boolean myOldRevision;
  private final List<TextRange> myRanges;
  private final FileType myFileType;
  private final int myAdditionalOffset;

  public TodoForRanges(final Project project, final String fileName, final String text, final boolean oldRevision,
                       final List<TextRange> ranges, FileType fileType, int additionalOffset) {
    myProject = project;
    myFileName = fileName;
    myText = text;
    myOldRevision = oldRevision;
    myRanges = ranges;
    myFileType = fileType;
    myAdditionalOffset = additionalOffset;
  }

  public List<Pair<TextRange, TextAttributes>> execute() {
    final TodoItem[] todoItems = ApplicationManager.getApplication().runReadAction(new Computable<TodoItem[]>() {
      @Override
      public TodoItem[] compute() {
        final PsiFile psiFile = PsiFileFactory.getInstance(myProject).createFileFromText((myOldRevision ? "old" : "") + myFileName, myFileType, myText);

        final PsiTodoSearchHelper helper = PsiTodoSearchHelper.SERVICE.getInstance(myProject);
        return helper.findTodoItemsLight(psiFile);
      }
    });
    
    final StepIntersection<TodoItem, TextRange> stepIntersection =
      new StepIntersection<TodoItem, TextRange>(new Convertor<TodoItem, TextRange>() {
        @Override
        public TextRange convert(TodoItem o) {
          return o.getTextRange();
        }
      }, Convertor.SELF, myRanges, new Getter<String>() {
        @Override
        public String get() {
          return "";
        }
      }
      );
    final List<TodoItem> filtered = stepIntersection.process(Arrays.asList(todoItems));
    final List<Pair<TextRange, TextAttributes>> result = new ArrayList<Pair<TextRange, TextAttributes>>(filtered.size());
    int offset = 0;
    for (TextRange range : myRanges) {
      Iterator<TodoItem> iterator = filtered.iterator();
      while (iterator.hasNext()) {
        TodoItem item = iterator.next();
        if (range.contains(item.getTextRange())) {
          TextRange todoRange = new TextRange(offset - range.getStartOffset() + item.getTextRange().getStartOffset(),
                                              offset - range.getStartOffset() + item.getTextRange().getEndOffset());
          result.add(new Pair<TextRange, TextAttributes>(todoRange, item.getPattern().getAttributes().getTextAttributes()));
          iterator.remove();
        } else {
          break;
        }
      }
      offset += range.getLength() + 1 + myAdditionalOffset;
    }
    return result;
  }
}
