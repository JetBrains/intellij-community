// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PlainPrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.VcsUserRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class CoAuthoredByCommitCompletionContributor extends CompletionContributor {

  private static final String CO_AUTHORED_BY = "Co-authored-by: ";

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiFile file = parameters.getOriginalFile();
    Project project = file.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) return;
    if (document.getUserData(CommitMessage.DATA_KEY) == null) return;

    String prefix = TextFieldWithAutoCompletionListProvider.getCompletionPrefix(parameters);
    CompletionResultSet prefixed = result.withPrefixMatcher(new PlainPrefixMatcher(prefix, true));

    int start = parameters.getOffset() - prefix.length();
    if (CO_AUTHORED_BY.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
      if (start > 0 && document.getCharsSequence().charAt(start - 1) == '\n') {
        prefixed.addElement(LookupElementBuilder.create(CO_AUTHORED_BY));
      }
    }
    if (start >= CO_AUTHORED_BY.length() + 1 &&
        document.getCharsSequence().subSequence(start - CO_AUTHORED_BY.length() - 1, start).toString()
          .equals("\n" + CO_AUTHORED_BY)) {
      result.stopHere();
      int count = parameters.getInvocationCount();
      if (count > 0 || !prefix.isEmpty()) {
        CompletionResultSet usersSet = result.withPrefixMatcher(new PlainPrefixMatcher(prefix, count == 0));
        for (VcsUser user : project.getService(VcsUserRegistry.class).getUsers()) {
          usersSet.addElement(LookupElementBuilder.create(user.toString()).withIcon(AllIcons.General.User));
        }
      }
    }
  }
}
