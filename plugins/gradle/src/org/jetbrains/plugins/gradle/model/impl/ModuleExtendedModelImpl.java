/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.model.impl;

import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 11/8/13
 */
public class ModuleExtendedModelImpl implements ModuleExtendedModel {
  private final String name;
  private final String group;
  private final String version;
  private List<File> myArtifacts;

  public ModuleExtendedModelImpl(String name, String group, String version) {
    this.name = name;
    this.group = group;
    this.version = version;
    this.myArtifacts = Collections.emptyList();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getGroup() {
    return group;
  }

  @Override
  public String getVersion() {
    return version;
  }

  @Override
  public List<File> getArtifacts() {
    return myArtifacts;
  }

  public void setArtifacts(List<File> artifacts) {
    this.myArtifacts = artifacts == null ? Collections.<File>emptyList() : artifacts;
  }
}
