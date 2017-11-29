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
package com.intellij.openapi.vcs;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class CommitCompletionContributor extends CompletionContributor {

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiFile file = parameters.getOriginalFile();
    Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document == null) return;

    CommitMessage commitMessage = document.getUserData(CommitMessage.DATA_KEY);
    if (commitMessage == null) return;

    result.stopHere();
    if (parameters.getInvocationCount() <= 0) return;

    List<ChangeList> lists = commitMessage.getChangeLists();
    if (lists.isEmpty()) return;

    String prefix = TextFieldWithAutoCompletionListProvider.getCompletionPrefix(parameters);
    CompletionResultSet insensitive = result.caseInsensitive().withPrefixMatcher(new CamelHumpMatcher(prefix));
    for (ChangeList list : lists) {
      ProgressManager.checkCanceled();
      for (Change change : list.getChanges()) {
        ProgressManager.checkCanceled();
        ContentRevision revision = change.getAfterRevision() == null ? change.getBeforeRevision() : change.getAfterRevision();
        if (revision != null) {
          FilePath filePath = revision.getFile();
          LookupElementBuilder element = LookupElementBuilder.create(filePath.getName()).
              withIcon(filePath.getFileType().getIcon());
          insensitive.addElement(element);
        }
      }
    }
  }
}
