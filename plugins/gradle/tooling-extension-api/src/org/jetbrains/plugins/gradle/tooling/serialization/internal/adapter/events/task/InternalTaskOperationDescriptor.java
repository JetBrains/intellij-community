// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.task;

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.PluginIdentifier;
import org.gradle.tooling.events.task.TaskOperationDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.Supplier;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.Suppliers;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalOperationDescriptor;

import java.util.Set;

@ApiStatus.Internal
public final class InternalTaskOperationDescriptor extends InternalOperationDescriptor implements TaskOperationDescriptor {
  private final String taskPath;
  private final Supplier<Set<OperationDescriptor>> dependencies;
  private final Supplier<PluginIdentifier> originPlugin;

  public InternalTaskOperationDescriptor(String id,
                                         String name,
                                         String displayName,
                                         OperationDescriptor parent,
                                         String taskPath,
                                         Supplier<Set<OperationDescriptor>> dependencies,
                                         Supplier<PluginIdentifier> originPlugin) {
    super(id, name, displayName, parent);
    this.taskPath = taskPath;
    this.dependencies = Suppliers.wrap(dependencies);
    this.originPlugin = Suppliers.wrap(originPlugin);
  }

  @Override
  public String getTaskPath() {
    return this.taskPath;
  }

  @Override
  public Set<? extends OperationDescriptor> getDependencies() {
    return this.dependencies.get();
  }

  @Override
  public PluginIdentifier getOriginPlugin() {
    return this.originPlugin.get();
  }
}
