// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation.structure;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.xml.XmlStructureViewBuilderProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

final class PluginDescriptorXmlStructureViewBuilderProvider implements XmlStructureViewBuilderProvider {
  @Override
  public StructureViewBuilder createStructureViewBuilder(@NotNull XmlFile file) {
    if (!DescriptorUtil.isPluginXml(file)) {
      return null;
    }

    return new TreeBasedStructureViewBuilder() {
      @Override
      public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new PluginDescriptorXmlStructureViewModel(file, editor);
      }

      @Override
      public boolean isRootNodeShown() {
        return false;
      }
    };
  }
}
