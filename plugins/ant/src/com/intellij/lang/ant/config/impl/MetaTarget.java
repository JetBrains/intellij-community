/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.execution.ExecutionHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MetaTarget implements AntBuildTargetBase {
  private final AntBuildFileBase myBuildFile;
  private final List<String> myTargets;
  private final @Nls String myName;
  private final @Nls String myDescription;

  public MetaTarget(final AntBuildFileBase buildFile, @Nls final String displayName, final List<String> targets) {
    myBuildFile = buildFile;
    myTargets = targets;
    myName = displayName;
    myDescription = AntBundle.message("meta.target.build.sequence.name.display.name", displayName);
  }

  @Override
  public Project getProject() {
    return myBuildFile.getProject();
  }

  public AntBuildFile getBuildFile() {
    return myBuildFile;
  }

  @NotNull
  @Override
  public List<@NlsSafe String> getTargetNames() {
    return myTargets;
  }

  @Override
  public @NlsSafe String getName() {
    return myName;
  }

  @Override
  @Nullable
  public @NlsSafe String getDisplayName() {
    return getName();
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) String getNotEmptyDescription() {
    return myDescription;
  }

  @Override
  public boolean isDefault() {
    return false;
  }

  @Override
  public @NonNls String getActionId() {
    final String modelName = myBuildFile.getModel().getName();
    if (modelName == null || modelName.length() == 0) {
      return null;
    }
    return AntConfiguration.getActionIdPrefix(myBuildFile.getProject()) +
           "_" +
           modelName +
           '_' +
           getName();
  }

  @Override
  public AntBuildModelBase getModel() {
    return myBuildFile.getModel();
  }

  @Override
  @Nullable
  public Navigatable getOpenFileDescriptor() {
    return null;
  }

  @Override
  @Nullable
  public BuildTask findTask(final String taskName) {
    return null;
  }

  @Override
  public void run(DataContext dataContext, List<BuildFileProperty> additionalProperties, AntBuildListener buildListener) {
    ExecutionHandler.runBuild(myBuildFile, myTargets, null, dataContext, additionalProperties, buildListener);
  }

  @Override
  @Nullable
  public VirtualFile getContainingFile() {
    return myBuildFile.getVirtualFile();
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final MetaTarget that = (MetaTarget)o;

    if (!myBuildFile.equals(that.myBuildFile)) {
      return false;
    }
    if (!Comparing.equal(myTargets, that.myTargets)) {
      return false;
    }

    return true;
  }

  public int hashCode() {
    int code = myBuildFile.hashCode();
    for (String name : myTargets) {
      code += name.hashCode();
    }
    return code;
  }
}
