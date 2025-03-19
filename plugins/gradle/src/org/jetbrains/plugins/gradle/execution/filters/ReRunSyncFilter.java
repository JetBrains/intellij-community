// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.filters;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class ReRunSyncFilter extends GradleReRunBuildFilter {
  private final ExternalSystemResolveProjectTask myTask;
  private final Project myProject;

  public ReRunSyncFilter(ExternalSystemResolveProjectTask task, Project project) {
    super(task.getExternalProjectPath());
    myTask = task;
    myProject = project;
  }

  @Override
  protected @NotNull HyperlinkInfo getHyperLinkInfo(List<String> options) {
    return (project) -> {
      ExternalSystemUtil.refreshProject(myTask.getExternalProjectPath(),
                                        new ImportSpecBuilder(myProject, myTask.getExternalSystemId())
                                          .withArguments(StringUtil.join(options, " ")));
    };
  }
}
