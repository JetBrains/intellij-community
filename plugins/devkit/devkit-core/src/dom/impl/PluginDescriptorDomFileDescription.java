// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.devkit.core.icons.DevkitCoreIcons;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import javax.swing.*;

final class PluginDescriptorDomFileDescription extends DomFileDescription<IdeaPlugin> {

  PluginDescriptorDomFileDescription() {
    super(IdeaPlugin.class, IdeaPlugin.TAG_NAME);
  }

  @Override
  public @Nullable Icon getFileIcon(@NotNull XmlFile file, @Iconable.IconFlags int flags) {
    IdeaPlugin ideaPlugin = DescriptorUtil.getIdeaPlugin(file);
    if (ideaPlugin == null) return null;

    if (ideaPlugin.isV2Descriptor()) {
      return DevkitCoreIcons.PluginV2;
    }

    return AllIcons.Nodes.Plugin;
  }
}
