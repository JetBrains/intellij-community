/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.project.model.impl.module;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.RootModelBase;
import com.intellij.project.model.impl.module.content.JpsContentEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class JpsRootModel extends RootModelBase implements ModuleRootModel {
  private final Module myModule;
  private final List<ContentEntry> myContentEntries;

  public JpsRootModel(Module module, JpsModule jpsModule) {
    myModule = module;
    myContentEntries = new ArrayList<>();
    for (String contentRoot : jpsModule.getContentRootsList().getUrls()) {
      myContentEntries.add(new JpsContentEntry(jpsModule, this, contentRoot));
    }
  }

  @NotNull
  @Override
  public Module getModule() {
    return myModule;
  }

  @Override
  protected Collection<ContentEntry> getContent() {
    return myContentEntries;
  }

  @Override
  public OrderEntry @NotNull [] getOrderEntries() {
    return OrderEntry.EMPTY_ARRAY;
  }

  @Override
  public <T> T getModuleExtension(@NotNull Class<T> klass) {
    throw new UnsupportedOperationException("'getModuleExtension' not implemented in " + getClass().getName());
  }

  public Project getProject() {
    return myModule.getProject();
  }
}
