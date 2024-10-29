// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class VcsEP implements PluginAware {
  public static final ExtensionPointName<VcsEP> EP_NAME = new ExtensionPointName<>("com.intellij.vcs");

  // these must be public for scrambling compatibility
  @Attribute("name")
  public String name;
  @Attribute("vcsClass")
  public String vcsClass;
  @Attribute("displayName")
  public String displayName;
  @Attribute("administrativeAreaName")
  public String administrativeAreaName;
  @Attribute("areChildrenValidMappings")
  public boolean areChildrenValidMappings;

  private PluginDescriptor pluginDescriptor;

  public @NotNull AbstractVcs createVcs(@NotNull Project project) {
    return project.instantiateClass(vcsClass, pluginDescriptor);
  }

  public @NotNull VcsDescriptor createDescriptor() {
    return new VcsDescriptor(administrativeAreaName, displayName, name, areChildrenValidMappings);
  }

  @Override
  @Transient
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }
}
