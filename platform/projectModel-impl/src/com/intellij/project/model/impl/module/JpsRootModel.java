// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.project.model.impl.module;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.RootModelBase;
import com.intellij.project.model.impl.module.content.JpsContentEntry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
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

  @Override
  public @NotNull Module getModule() {
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
