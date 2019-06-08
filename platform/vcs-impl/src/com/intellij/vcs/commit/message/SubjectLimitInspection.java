// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.containers.ContainerUtil.ar;
import static java.lang.String.format;

public class SubjectLimitInspection extends BaseCommitMessageInspection {

  public int RIGHT_MARGIN = 72;

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Limit subject line";
  }

  @NotNull
  @Override
  public ConfigurableUi<Project> createOptionsConfigurable() {
    return new SubjectLimitInspectionOptions(this);
  }

  @Nullable
  @Override
  protected ProblemDescriptor[] checkFile(@NotNull PsiFile file,
                                          @NotNull Document document,
                                          @NotNull InspectionManager manager,
                                          boolean isOnTheFly) {
    ProblemDescriptor descriptor = checkRightMargin(file, document, manager, isOnTheFly, 0, RIGHT_MARGIN,
                                                    format("Subject should not exceed %d characters", RIGHT_MARGIN));

    return descriptor != null ? ar(descriptor) : null;
  }
}
