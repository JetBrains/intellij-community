// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PlainPrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.VcsUserRegistry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class CoAuthoredByCommitCompletionContributor extends CompletionContributor {

  private static final String[] PREFIXES = {"Co-authored-by: ", "Signed-off-by: ", "Reviewed-by: ", "Acked-by: ", "Tested-by: "};

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiFile file = parameters.getOriginalFile();
    Project project = file.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) return;
    if (!CommitMessage.isCommitMessage(document)) return;

    String prefix = TextFieldWithAutoCompletionListProvider.getCompletionPrefix(parameters);
    CompletionResultSet prefixed = result.withPrefixMatcher(new PlainPrefixMatcher(prefix, true));

    int start = parameters.getOffset() - prefix.length();
    CharSequence charSequence = document.getCharsSequence();
    for (String knownPrefix : PREFIXES) {
      if (StringUtil.startsWithIgnoreCase(knownPrefix, prefix) && start > 0 && charSequence.charAt(start - 1) == '\n') {
        prefixed.addElement(LookupElementBuilder.create(knownPrefix));
      }
      else if (start >= knownPrefix.length() + 1 &&
          charSequence.charAt(start - knownPrefix.length() - 1) == '\n' &&
          charSequence.subSequence(start - knownPrefix.length(), start).toString().equals(knownPrefix)) {
        result.stopHere();
        int count = parameters.getInvocationCount();
        if (count > 0 || !prefix.isEmpty()) {
          CompletionResultSet usersSet = result.withPrefixMatcher(new PlainPrefixMatcher(prefix, count == 0));
          for (VcsUser user : project.getService(VcsUserRegistry.class).getUsers()) {
            usersSet.addElement(LookupElementBuilder.create(user.toString()).withIcon(AllIcons.General.User));
          }
        }
        return;
      }
    }
  }
}
