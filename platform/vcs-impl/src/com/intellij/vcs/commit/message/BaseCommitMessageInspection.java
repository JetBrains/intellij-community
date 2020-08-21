// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message;

import com.intellij.codeInsight.daemon.impl.IntentionActionFilter;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.actionSystem.ShortcutProvider;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.VcsBundle;
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

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return VcsBundle.message("commit.message");
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

  @Override
  public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    Document document = getDocument(file);

    return document != null ? checkFile(file, document, manager, isOnTheFly) : null;
  }

  protected ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file,
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
                                               @NotNull @InspectionMessage String problemText,
                                               LocalQuickFix @NotNull ... fixes) {
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

  protected static abstract class BaseCommitMessageQuickFix implements LocalQuickFix {
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

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return VcsBundle.message("commit.message.intention.family.name.reformat.commit.message");
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