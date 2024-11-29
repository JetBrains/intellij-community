// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.ModuleDescriptorNameConverter;

import java.util.List;

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

    @NotNull
    @Stubbed
    GenericAttributeValue<ModuleLoadingRule> getLoading();
    
    enum ModuleLoadingRule implements NamedEnum {
      REQUIRED("required"), EMBEDDED("embedded"), OPTIONAL("optional"), ON_DEMAND("on-demand");

      private final String myValue;

      ModuleLoadingRule(@NotNull String value) {
        myValue = value;
      }

      @Override
      public String getValue() {
        return myValue;
      }
    }
  }
}
