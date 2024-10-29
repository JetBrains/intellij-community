// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.DocumentUtil.getLineTextRange;
import static com.intellij.util.containers.ContainerUtil.ar;

@ApiStatus.Internal
public class SubjectBodySeparationInspection extends BaseCommitMessageInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return VcsBundle.message("inspection.SubjectBodySeparationInspection.display.name");
  }

  @Override
  protected ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file,
                                                     @NotNull Document document,
                                                     @NotNull InspectionManager manager,
                                                     boolean isOnTheFly) {
    ProblemDescriptor descriptor = document.getLineCount() > 1
                                   ? checkRightMargin(file, document, manager, isOnTheFly, 1, 0,
                                                      VcsBundle.message("commit.message.missing.blank.line.between.subject.and.body"), new AddBlankLineQuickFix(),
                                                      new ReformatCommitMessageQuickFix())
                                   : null;

    return descriptor != null ? ar(descriptor) : null;
  }

  @Override
  public boolean canReformat(@NotNull Project project, @NotNull Document document) {
    return hasProblems(project, document);
  }

  @Override
  public void reformat(@NotNull Project project, @NotNull Document document) {
    new AddBlankLineQuickFix().doApplyFix(project, document, null);
  }

  protected static class AddBlankLineQuickFix extends BaseCommitMessageQuickFix {
    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return VcsBundle.message("settings.commit.message.body.add.blank.line.fix");
    }

    @Override
    public void doApplyFix(@NotNull Project project, @NotNull Document document, @Nullable ProblemDescriptor descriptor) {
      int line = descriptor != null && descriptor.getLineNumber() >= 0 ? descriptor.getLineNumber() : getFirstLine(document);

      if (line >= 0) {
        TextRange lineRange = getLineTextRange(document, line);

        if (!lineRange.isEmpty()) {
          document.insertString(lineRange.getStartOffset(), "\n");
        }
      }
    }

    private static int getFirstLine(@NotNull Document document) {
      return document.getLineCount() > 1 ? 1 : -1;
    }
  }
}
