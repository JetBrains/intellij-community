/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.Iconable;
import com.intellij.util.xml.DomFileDescription;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import javax.swing.*;

/**
 * @author mike
 */
public class PluginDescriptorDomFileDescription extends DomFileDescription<IdeaPlugin> {

  public PluginDescriptorDomFileDescription() {
    super(IdeaPlugin.class, "idea-plugin");
  }

  @Override
  public Icon getFileIcon(@Iconable.IconFlags int flags) {
    return AllIcons.Nodes.Plugin;
  }

  @Override
  public boolean hasStubs() {
    return true;
  }

  @Override
  public int getStubVersion() {
    return 8;
  }
}
