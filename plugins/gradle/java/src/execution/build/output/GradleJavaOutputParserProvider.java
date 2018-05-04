// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.build.output;

import com.intellij.build.output.BuildOutputParser;
import com.intellij.build.output.JavacOutputParser;
import com.intellij.build.output.KotlincOutputParser;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemOutputParserProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Collections;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class GradleJavaOutputParserProvider implements ExternalSystemOutputParserProvider {
  @Override
  public ProjectSystemId getExternalSystemId() {
    return GradleConstants.SYSTEM_ID;
  }

  @Override
  public List<BuildOutputParser> getBuildOutputParsers(ExternalSystemTask task) {
    if (task.getId().getType().equals(ExternalSystemTaskType.EXECUTE_TASK)) {
      return ContainerUtil.list(new JavacOutputParser(), new KotlincOutputParser());
    }
    else {
      return Collections.emptyList();
    }
  }
}
