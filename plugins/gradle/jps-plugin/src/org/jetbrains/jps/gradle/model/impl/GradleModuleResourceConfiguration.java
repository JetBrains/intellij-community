/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.gradle.model.impl;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


/**
 * @author Vladislav.Soroka
 * @since 7/10/2014
 */
public class GradleModuleResourceConfiguration {
  @NotNull
  @Tag("id")
  public ModuleVersion id;

  @Nullable
  @Tag("parentId")
  public ModuleVersion parentId;

  @OptionTag
  public boolean overwrite;

  @OptionTag
  public String outputDirectory = null;

  @Tag("resources")
  @AbstractCollection(surroundWithTag = false, elementTag = "resource")
  public List<ResourceRootConfiguration> resources = new ArrayList<ResourceRootConfiguration>();

  @Tag("test-resources")
  @AbstractCollection(surroundWithTag = false, elementTag = "resource")
  public List<ResourceRootConfiguration> testResources = new ArrayList<ResourceRootConfiguration>();

  public int computeConfigurationHash(boolean forTestResources) {
    int result = computeModuleConfigurationHash();

    final List<ResourceRootConfiguration> _resources = forTestResources ? testResources : resources;
    result = 31 * result;
    for (ResourceRootConfiguration resource : _resources) {
      result += resource.computeConfigurationHash();
    }
    return result;
  }

  public int computeConfigurationHash() {
    int result = computeModuleConfigurationHash();

    final List<ResourceRootConfiguration> _resources = ContainerUtil.concat(testResources, resources);
    result = 31 * result;
    for (ResourceRootConfiguration resource : _resources) {
      result += resource.computeConfigurationHash();
    }
    return result;
  }

  public int computeModuleConfigurationHash() {
    int result = id.hashCode();
    result = 31 * result + (parentId != null ? parentId.hashCode() : 0);
    result = 31 * result + (outputDirectory != null ? outputDirectory.hashCode() : 0);
    result = 31 * result + (overwrite ? 1 : 0);
    return result;
  }
}



