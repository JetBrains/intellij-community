// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.containers.ContainerUtil.ar;

@ApiStatus.Internal
public class SubjectLimitInspection extends BaseCommitMessageInspection {

  public int RIGHT_MARGIN = 72;

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return VcsBundle.message("inspection.SubjectLimitInspection.display.name");
  }

  @NotNull
  @Override
  public ConfigurableUi<Project> createOptionsConfigurable() {
    return new SubjectLimitInspectionOptions(this);
  }

  @Override
  protected ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file,
                                                     @NotNull Document document,
                                                     @NotNull InspectionManager manager,
                                                     boolean isOnTheFly) {
    String problemText = VcsBundle.message("commit.message.inspection.message.subject.should.not.exceed.characters", RIGHT_MARGIN);
    ProblemDescriptor descriptor = checkRightMargin(file, document, manager, isOnTheFly, 0, RIGHT_MARGIN,
                                                    problemText);

    return descriptor != null ? ar(descriptor) : null;
  }
}
