// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.ModuleDescriptorNameConverter;

import java.util.List;

@ApiStatus.Experimental
public interface ContentDescriptor extends DomElement {
  @NotNull
  @Stubbed
  @SubTagList("module")
  List<ModuleDescriptor> getModuleEntry();

  @SubTagList("module")
  ModuleDescriptor addModuleEntry();

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
