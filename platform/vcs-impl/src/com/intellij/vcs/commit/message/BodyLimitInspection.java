// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorFacade;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.util.TextRange.EMPTY_RANGE;
import static com.intellij.util.DocumentUtil.getLineTextRange;
import static java.util.Collections.singletonList;
import static java.util.stream.IntStream.range;

public class BodyLimitInspection extends BaseCommitMessageInspection {

  public int RIGHT_MARGIN = 72;

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return VcsBundle.message("inspection.BodyLimitInspection.display.name");
  }

  @NotNull
  @Override
  public ConfigurableUi<Project> createOptionsConfigurable() {
    return new BodyLimitInspectionOptions(this);
  }

  @Override
  protected ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file,
                                                     @NotNull Document document,
                                                     @NotNull InspectionManager manager,
                                                     boolean isOnTheFly) {
    return range(1, document.getLineCount())
      .mapToObj(line -> {
        String problemText = VcsBundle.message("commit.message.inspection.message.body.lines.should.not.exceed.characters", RIGHT_MARGIN);
        return checkRightMargin(file, document, manager, isOnTheFly, line, RIGHT_MARGIN,
                                problemText, new WrapLineQuickFix(),
                                new ReformatCommitMessageQuickFix());
      })
      .filter(Objects::nonNull)
      .toArray(ProblemDescriptor[]::new);
  }

  @Override
  public boolean canReformat(@NotNull Project project, @NotNull Document document) {
    return hasProblems(project, document);
  }

  @Override
  public void reformat(@NotNull Project project, @NotNull Document document) {
    new WrapLineQuickFix().doApplyFix(project, document, null);
  }

  protected class WrapLineQuickFix extends BaseCommitMessageQuickFix {
    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return VcsBundle.message("commit.message.intention.family.name.wrap.line");
    }

    @Override
    public void doApplyFix(@NotNull Project project, @NotNull Document document, @Nullable ProblemDescriptor descriptor) {
      Editor editor = CommitMessage.getEditor(document);

      if (editor != null) {
        TextRange range = descriptor != null && descriptor.getLineNumber() >= 0
                          ? getLineTextRange(document, descriptor.getLineNumber())
                          : getBodyRange(document);

        if (!range.isEmpty()) {
          wrapLines(project, editor, document, RIGHT_MARGIN, range);
        }
      }
    }

    @NotNull
    private TextRange getBodyRange(@NotNull Document document) {
      return document.getLineCount() > 1 ? TextRange.create(document.getLineStartOffset(1), document.getTextLength()) : EMPTY_RANGE;
    }

    private void wrapLines(@NotNull Project project,
                           @NotNull Editor editor,
                           @NotNull Document document,
                           int rightMargin,
                           @NotNull TextRange range) {
      List<TextRange> enabledRanges = singletonList(TextRange.create(0, document.getTextLength()));
      EditorFacade.getInstance().doWrapLongLinesIfNecessary(editor, project, document, range.getStartOffset(), range.getEndOffset(),
                                                            enabledRanges, rightMargin);
    }
  }
}