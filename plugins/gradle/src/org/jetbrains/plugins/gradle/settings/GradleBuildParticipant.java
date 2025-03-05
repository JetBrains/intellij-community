// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.settings;

import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 */
public final class GradleBuildParticipant implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String myProjectPath;
  private final Map<File, ModuleData> moduleArtifactMap = CollectionFactory.createCustomHashingStrategyMap(new HashingStrategy<>() {
    @Override
    public int hashCode(File file) {
      return ExternalSystemUtil.fileHashCode(file);
    }

    @Override
    public boolean equals(File o1, File o2) {
      return ExternalSystemUtil.filesEqual(o1, o2);
    }
  });
  private final Map<String, ModuleData> moduleNameMap = new HashMap<>();

  public GradleBuildParticipant(String projectPath) {
    myProjectPath = projectPath;
  }

  public String getProjectPath() {
    return myProjectPath;
  }

  public void addModule(ModuleData moduleData) {
    List<File> artifacts = moduleData.getArtifacts();
    if (!artifacts.isEmpty()) {
      for (File artifact : artifacts) {
        moduleArtifactMap.put(artifact, moduleData);
      }
    }
    moduleNameMap.put(moduleData.getExternalName(), moduleData);
    if (moduleData instanceof GradleSourceSetData) {
      String mainModuleName = StringUtil.trimEnd(moduleData.getExternalName(), ":main");
      moduleNameMap.put(mainModuleName, moduleData);
    }
  }

  public @Nullable ModuleData findModuleDataByArtifacts(Collection<? extends File> artifacts) {
    ModuleData moduleData = null;
    for (File artifact : artifacts) {
      moduleData = moduleArtifactMap.get(artifact);
      if (moduleData != null) break;
    }
    return moduleData;
  }

  public @Nullable ModuleData findModuleDataByName(String moduleName) {
    return moduleNameMap.get(moduleName);
  }
}
