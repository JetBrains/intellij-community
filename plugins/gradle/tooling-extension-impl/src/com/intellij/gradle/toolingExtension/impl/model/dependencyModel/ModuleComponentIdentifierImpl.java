// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyModel;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
public class ModuleComponentIdentifierImpl implements ModuleComponentIdentifier {
  @NotNull
  private final String group;
  @NotNull
  private final String module;
  @NotNull
  private final String version;
  @NotNull
  private final ModuleIdentifier moduleIdentifier;

  public ModuleComponentIdentifierImpl(@NotNull String group, @NotNull String module, @NotNull String version) {
    this.group = group;
    this.module = module;
    this.version = version;
    this.moduleIdentifier = new ModuleIdentifierImpl(group, module);
  }

  @Override
  public String getDisplayName() {
    return group + ":" + module + ":" + version;
  }

  @Override
  @NotNull
  public String getGroup() {
    return group;
  }

  @Override
  @NotNull
  public String getModule() {
    return module;
  }

  @Override
  @NotNull
  public String getVersion() {
    return version;
  }

  @NotNull
  public ModuleIdentifier getModuleIdentifier() {
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
}
