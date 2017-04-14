/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.commit;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.IntentionActionFilter;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.longLine.LongLineInspection;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
import static com.intellij.openapi.vcs.ui.CommitMessage.isCommitMessage;

public abstract class BaseCommitMessageInspection extends LocalInspectionTool {

  public static final String GROUP_NAME = "Commit message";

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GROUP_NAME;
  }

  @Nullable
  @Override
  public String getStaticDescription() {
    return "";
  }

  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element) {
    return !isCommitMessage(element);
  }

  @Nullable
  public ConfigurableUi<Project> createOptionsConfigurable() {
    return null;
  }

  @Nullable
  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    Document document = getDocument(file);

    return document != null ? checkFile(file, document, manager, isOnTheFly) : null;
  }

  @Nullable
  protected ProblemDescriptor[] checkFile(@NotNull PsiFile file,
                                          @NotNull Document document,
                                          @NotNull InspectionManager manager,
                                          boolean isOnTheFly) {
    return null;
  }

  @Nullable
  protected static ProblemDescriptor checkRightMargin(@NotNull PsiFile file,
                                               @NotNull Document document,
                                               @NotNull InspectionManager manager,
                                               boolean isOnTheFly,
                                               int line,
                                               int rightMargin,
                                               @NotNull String problemText) {
    TextRange exceedingRange = LongLineInspection.getExceedingRange(document, line, rightMargin);

    return !exceedingRange.isEmpty() ? manager
      .createProblemDescriptor(file, exceedingRange, problemText, GENERIC_ERROR_OR_WARNING, isOnTheFly) : null;
  }

  @Nullable
  private static Document getDocument(@NotNull PsiElement element) {
    return PsiDocumentManager.getInstance(element.getProject()).getDocument(element.getContainingFile());
  }

  public static class EmptyIntentionActionFilter implements IntentionActionFilter {
    @Override
    public boolean accept(@NotNull IntentionAction intentionAction, @Nullable PsiFile file) {
      return file == null || !isCommitMessage(file) || !(intentionAction instanceof EmptyIntentionAction);
    }
  }
}