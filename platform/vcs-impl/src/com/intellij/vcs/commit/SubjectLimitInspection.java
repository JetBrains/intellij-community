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
