// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyModel;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
public class DefaultModuleComponentIdentifier implements ModuleComponentIdentifier {

  private final @NotNull String group;
  private final @NotNull String module;
  private final @NotNull String version;
  private final @NotNull ModuleIdentifier moduleIdentifier;

  public DefaultModuleComponentIdentifier(@NotNull String group, @NotNull String module, @NotNull String version) {
    this.group = group;
    this.module = module;
    this.version = version;
    this.moduleIdentifier = new DefaultModuleIdentifier(group, module);
  }

  @Override
  public @NotNull String getDisplayName() {
    return group + ":" + module + ":" + version;
  }

  @Override
  public @NotNull String getGroup() {
    return group;
  }

  @Override
  public @NotNull String getModule() {
    return module;
  }

  @Override
  public @NotNull String getVersion() {
    return version;
  }

  @Override
  public @NotNull ModuleIdentifier getModuleIdentifier() {
    return moduleIdentifier;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModuleComponentIdentifier)) return false;

    ModuleComponentIdentifier that = (ModuleComponentIdentifier)o;

    if (!group.equals(that.getGroup())) return false;
    if (!module.equals(that.getModule())) return false;
    if (!version.equals(that.getVersion())) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = group.hashCode();
    result = 31 * result + module.hashCode();
    result = 31 * result + version.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return getDisplayName();
  }

  public static ModuleComponentIdentifier create(ModuleVersionIdentifier id) {
    return new DefaultModuleComponentIdentifier(id.getGroup(), id.getName(), id.getVersion());
  }
}
