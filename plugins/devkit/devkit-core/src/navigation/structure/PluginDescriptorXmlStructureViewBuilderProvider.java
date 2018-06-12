// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
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

public class PluginDescriptorXmlStructureViewBuilderProvider implements XmlStructureViewBuilderProvider {
  @Override
  public StructureViewBuilder createStructureViewBuilder(@NotNull XmlFile file) {
    if (!DescriptorUtil.isPluginXml(file)) {
      return null;
    }

    return new TreeBasedStructureViewBuilder() {
      @NotNull
      @Override
      public StructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new PluginDescriptorXmlStructureViewModel(file, editor);
      }

      @Override
      public boolean isRootNodeShown() {
        return false;
      }
    };
  }
}
