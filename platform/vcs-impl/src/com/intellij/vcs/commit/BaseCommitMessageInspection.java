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

import com.intellij.codeInsight.daemon.impl.IntentionActionFilter;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.*;
import com.intellij.openapi.actionSystem.ShortcutProvider;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;
import static com.intellij.openapi.vcs.ui.CommitMessage.isCommitMessage;
import static com.intellij.util.ArrayUtil.isEmpty;

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
                                               @NotNull String problemText,
                                               @NotNull LocalQuickFix... fixes) {
    int start = document.getLineStartOffset(line);
    int end = document.getLineEndOffset(line);

    if (end > start + rightMargin) {
      TextRange exceedingRange = new TextRange(start + rightMargin, end);
      return manager.createProblemDescriptor(file, exceedingRange, problemText, GENERIC_ERROR_OR_WARNING, isOnTheFly, fixes);
    }
    return null;
  }

  public boolean canReformat(@NotNull Project project, @NotNull Document document) {
    return false;
  }

  public void reformat(@NotNull Project project, @NotNull Document document) {
  }

  protected boolean hasProblems(@NotNull Project project, @NotNull Document document) {
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);

    return file != null && !isEmpty(checkFile(file, document, InspectionManager.getInstance(project), false));
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

  protected static abstract class BaseCommitMessageQuickFix extends LocalQuickFixBase {
    protected BaseCommitMessageQuickFix(@NotNull String name) {
      super(name);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      Document document = getDocument(descriptor.getPsiElement());

      if (document != null) {
        doApplyFix(project, document, descriptor);
      }
    }

    public abstract void doApplyFix(@NotNull Project project, @NotNull Document document, @Nullable ProblemDescriptor descriptor);
  }

  protected static class ReformatCommitMessageQuickFix extends BaseCommitMessageQuickFix
    implements LowPriorityAction, IntentionAction, ShortcutProvider {
    protected ReformatCommitMessageQuickFix() {
      super(ReformatCommitMessageAction.NAME);
    }

    @Override
    public void doApplyFix(@NotNull Project project, @NotNull Document document, @Nullable ProblemDescriptor descriptor) {
      ReformatCommitMessageAction.reformat(project, document);
    }

    @Nullable
    @Override
    public ShortcutSet getShortcut() {
      return getActiveKeymapShortcuts("Vcs.ReformatCommitMessage");
    }

    @Nls
    @NotNull
    @Override
    public String getText() {
      return getName();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    @Override
    public void invoke(@NotNull Project project, @Nullable Editor editor, @NotNull PsiFile file) throws IncorrectOperationException {
      Document document = getDocument(file);

      if (document != null) {
        doApplyFix(project, document, null);
      }
    }

    @Override
    public boolean startInWriteAction() {
      return true;
    }
  }
}