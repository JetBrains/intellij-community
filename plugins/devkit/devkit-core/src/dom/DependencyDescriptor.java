// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.IdeaPluginConverter;
import org.jetbrains.idea.devkit.dom.impl.ModuleDescriptorNameConverter;

import java.util.List;

@Presentation(icon = "AllIcons.Nodes.Related")
public interface DependencyDescriptor extends DomElement {

  @NotNull
  @Stubbed
  @SubTagList("module")
  List<ModuleDescriptor> getModuleEntry();

  @SubTagList("module")
  ModuleDescriptor addModuleEntry();

  @NotNull
  @Stubbed
  @SubTagList("plugin")
  List<PluginDescriptor> getPlugin();

  @SubTagList("plugin")
  PluginDescriptor addPlugin();

  @Presentation(icon = "AllIcons.Nodes.Plugin")
  interface PluginDescriptor extends DomElement {

    @NotNull
    @Required
    @Stubbed
    @Convert(IdeaPluginConverter.class)
    @NameValue(referencable = false)
    GenericAttributeValue<IdeaPlugin> getId();
  }

  @Presentation(icon = "AllIcons.Nodes.Module")
  interface ModuleDescriptor extends DomElement {
    @NotNull
    @Required
    @Stubbed
    @NameValue(referencable = false)
    @Convert(ModuleDescriptorNameConverter.class)
    GenericAttributeValue<IdeaPlugin> getName();
  }
}
