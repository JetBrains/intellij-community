// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.Iconable;
import com.intellij.util.xml.DomFileDescription;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import javax.swing.*;

final class PluginDescriptorDomFileDescription extends DomFileDescription<IdeaPlugin> {

  PluginDescriptorDomFileDescription() {
    super(IdeaPlugin.class, IdeaPlugin.TAG_NAME);
  }

  @Override
  public Icon getFileIcon(@Iconable.IconFlags int flags) {
    return AllIcons.Nodes.Plugin;
  }

}
