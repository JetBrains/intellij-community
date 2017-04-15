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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.DocumentUtil.getLineTextRange;
import static com.intellij.util.containers.ContainerUtil.ar;

public class SubjectBodySeparationInspection extends BaseCommitMessageInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Blank line between subject and body";
  }

  @Nullable
  @Override
  protected ProblemDescriptor[] checkFile(@NotNull PsiFile file,
                                          @NotNull Document document,
                                          @NotNull InspectionManager manager,
                                          boolean isOnTheFly) {
    ProblemDescriptor descriptor = document.getLineCount() > 1
                                   ? checkRightMargin(file, document, manager, isOnTheFly, 1, 0,
                                                      "Missing blank line between subject and body", new AddBlankLineQuickFix(),
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
    protected AddBlankLineQuickFix() {
      super("Add blank line");
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
