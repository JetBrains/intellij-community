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
package org.jetbrains.plugins.gradle.execution.build;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.BooleanFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;

import java.util.Map;

/**
 * @author Vladislav.Soroka
 * @since 5/14/2016
 */
public class CachedModuleDataFinder {
  private Map<String, DataNode<ModuleData>> cache = ContainerUtil.newHashMap();

  @Nullable
  public DataNode<ModuleData> findModuleData(final DataNode parentNode, final String projectPath) {
    DataNode<ModuleData> node = cache.get(projectPath);
    if (node != null) return node;

    //noinspection unchecked
    return (DataNode<ModuleData>)ExternalSystemApiUtil.findFirstRecursively(parentNode, new BooleanFunction<DataNode<?>>() {
      @Override
      public boolean fun(DataNode<?> node) {
        if ((ProjectKeys.MODULE.equals(node.getKey()) ||
             GradleSourceSetData.KEY.equals(node.getKey())) && node.getData() instanceof ModuleData) {
          String externalProjectPath = ((ModuleData)node.getData()).getLinkedExternalProjectPath();
          //noinspection unchecked
          DataNode<ModuleData> myNode = (DataNode<ModuleData>)node;
          cache.put(externalProjectPath, myNode);

          return StringUtil.equals(projectPath, ((ModuleData)node.getData()).getLinkedExternalProjectPath());
        }

        return false;
      }
    });
  }
}
