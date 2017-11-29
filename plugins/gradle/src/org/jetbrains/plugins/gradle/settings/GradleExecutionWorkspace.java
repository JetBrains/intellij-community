/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.settings;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 * @since 10/7/2016
 */
public class GradleExecutionWorkspace implements Serializable {
  private static final long serialVersionUID = 1L;

  @NotNull
  private final List<GradleBuildParticipant> myBuildParticipants = ContainerUtil.newArrayList();
  private Map<String, Pair<DataNode<ModuleData>, IdeaModule>> myModuleMap;

  public void addBuildParticipant(GradleBuildParticipant participant) {
    myBuildParticipants.add(participant);
  }

  @NotNull
  public List<GradleBuildParticipant> getBuildParticipants() {
    return Collections.unmodifiableList(myBuildParticipants);
  }

  @Nullable
  public ModuleData findModuleDataByArtifacts(Collection<File> artifacts) {
    ModuleData result = null;
    for (GradleBuildParticipant buildParticipant : myBuildParticipants) {
      result = buildParticipant.findModuleDataByArtifacts(artifacts);
      if (result != null) break;
    }
    return result;
  }

  public ModuleData findModuleDataByName(String moduleName) {
    ModuleData result = null;

    Pair<DataNode<ModuleData>, IdeaModule> modulePair = myModuleMap.get(moduleName);
    if(modulePair == null) {
      modulePair = myModuleMap.get(":" + moduleName);
    }
    if (modulePair != null) {
      return modulePair.first.getData();
    }

    for (GradleBuildParticipant buildParticipant : myBuildParticipants) {
      result = buildParticipant.findModuleDataByName(moduleName);
      if (result != null) break;
    }
    return result;
  }

  public void addModuleMap(Map<String, Pair<DataNode<ModuleData>, IdeaModule>> moduleMap) {
    myModuleMap = moduleMap;
  }
}
