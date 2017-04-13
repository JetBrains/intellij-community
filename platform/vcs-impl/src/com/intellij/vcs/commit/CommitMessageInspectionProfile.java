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

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class CommitMessageInspectionProfile extends InspectionProfileImpl {

  @NotNull private final Project myProject;

  public CommitMessageInspectionProfile(@NotNull Project project) {
    super("Commit Dialog", new CommitMessageInspectionToolRegistrar(), (InspectionProfileImpl)null);
    myProject = project;
  }

  @NotNull
  public static CommitMessageInspectionProfile getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, CommitMessageInspectionProfile.class);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  private static class CommitMessageInspectionToolRegistrar extends InspectionToolRegistrar {
    @NotNull
    @Override
    public List<InspectionToolWrapper> createTools() {
      return Stream.of(new SubjectBodySeparationInspection(), new SubjectLimitInspection(), new BodyLimitInspection())
        .map(LocalInspectionToolWrapper::new)
        .collect(toList());
    }
  }
}
