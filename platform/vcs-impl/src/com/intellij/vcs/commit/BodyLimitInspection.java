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

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.util.TextRange.EMPTY_RANGE;
import static com.intellij.util.DocumentUtil.getLineTextRange;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.stream.IntStream.range;

public class BodyLimitInspection extends BaseCommitMessageInspection {

  public int RIGHT_MARGIN = 72;

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Limit body line";
  }

  @NotNull
  @Override
  public ConfigurableUi<Project> createOptionsConfigurable() {
    return new BodyLimitInspectionOptions(this);
  }

  @Nullable
  @Override
  protected ProblemDescriptor[] checkFile(@NotNull PsiFile file,
                                          @NotNull Document document,
                                          @NotNull InspectionManager manager,
                                          boolean isOnTheFly) {
    return range(1, document.getLineCount())
      .mapToObj(line -> checkRightMargin(file, document, manager, isOnTheFly, line, RIGHT_MARGIN,
                                         format("Body lines should not exceed %d characters", RIGHT_MARGIN), new WrapLineQuickFix(),
                                         new ReformatCommitMessageQuickFix()))
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
    protected WrapLineQuickFix() {
      super("Wrap line");
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
      CodeFormatterFacade codeFormatter = new CodeFormatterFacade(new CodeStyleSettings(false) {
        @Override
        public int getRightMargin(@Nullable Language language) {
          return rightMargin;
        }
      }, null);
      List<TextRange> enabledRanges = singletonList(TextRange.create(0, document.getTextLength()));

      codeFormatter.doWrapLongLinesIfNecessary(editor, project, document, range.getStartOffset(), range.getEndOffset(), enabledRanges);
    }
  }
}