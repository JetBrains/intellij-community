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

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class CommitCompletionContributor extends CompletionContributor {

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiFile file = parameters.getOriginalFile();
    Project project = file.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) return;

    CommitMessage commitMessage = document.getUserData(CommitMessage.DATA_KEY);
    if (commitMessage == null) return;

    result.stopHere();
    int count = parameters.getInvocationCount();

    List<ChangeList> lists = commitMessage.getChangeLists();
    if (lists.isEmpty()) return;

    String prefix = TextFieldWithAutoCompletionListProvider.getCompletionPrefix(parameters);
    if (count == 0 && prefix.length() < 5) return;
    CompletionResultSet insensitive = result.caseInsensitive().withPrefixMatcher(
      count == 0 ? new PlainPrefixMatcher(prefix, true) : new CamelHumpMatcher(prefix));
    CompletionResultSet prefixed = result.withPrefixMatcher(new PlainPrefixMatcher(prefix, count == 0));
    for (ChangeList list : lists) {
      ProgressManager.checkCanceled();
      for (Change change : list.getChanges()) {
        ProgressManager.checkCanceled();
        if (change.getAfterRevision() == null) {
          ContentRevision revision = change.getBeforeRevision();
          if (revision != null) {
            FilePath filePath = revision.getFile();
            LookupElementBuilder element = LookupElementBuilder.create(filePath.getName())
              .withStrikeoutness(true).withIcon(filePath.getFileType().getIcon());
            insensitive.addElement(element);
          }
        }
        else {
          ContentRevision revision = change.getAfterRevision();
          if (revision != null) {
            FilePath filePath = revision.getFile();
            LookupElementBuilder element = LookupElementBuilder.create(filePath.getName())
              .withIcon(filePath.getFileType().getIcon());
            insensitive.addElement(element);
            ContentRevision beforeRevision = change.getBeforeRevision();
            if (beforeRevision != null) {
              FilePath beforeFile = beforeRevision.getFile();
              if (!beforeFile.getName().equals(filePath.getName())) {
                insensitive.addElement(LookupElementBuilder.create(beforeFile.getName())
                  .withStrikeoutness(true).withIcon(beforeFile.getFileType().getIcon()));
              }
            }
            addLanguageSpecificElements(project, count, prefixed, filePath);
          }
        }
      }

      if (count > 0) {
        result.caseInsensitive()
          .withPrefixMatcher(new PlainPrefixMatcher(prefix))
          .addAllElements(
            StreamEx.of(VcsConfiguration.getInstance(project).getRecentMessages())
              .reverseSorted()
              .map(lookupString -> PrioritizedLookupElement.withPriority(LookupElementBuilder.create(lookupString), Integer.MIN_VALUE)));
      }
    }
  }

  private static void addLanguageSpecificElements(Project project, int count, CompletionResultSet prefixed, FilePath filePath) {
    VirtualFile vFile = filePath.getVirtualFile();
    if (vFile == null) return;
    PsiFile psiFile = PsiManagerEx.getInstanceEx(project).findFile(vFile);
    if (psiFile == null) return;
    PlainTextSymbolCompletionContributor contributor = PlainTextSymbolCompletionContributorEP.forLanguage(psiFile.getLanguage());
    if (contributor == null) return;
    prefixed.addAllElements(contributor.getLookupElements(psiFile, count, prefixed.getPrefixMatcher().getPrefix()));
  }
}
