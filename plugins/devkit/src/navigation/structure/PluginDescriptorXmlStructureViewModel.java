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
package org.jetbrains.idea.devkit.navigation.structure;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.xml.XmlStructureViewTreeModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

public class PluginDescriptorXmlStructureViewModel extends XmlStructureViewTreeModel implements StructureViewModel.ElementInfoProvider {
  public PluginDescriptorXmlStructureViewModel(@NotNull XmlFile file, @Nullable Editor editor) {
    super(file, editor);
  }


  @NotNull
  @Override
  public StructureViewTreeElement getRoot() {
    XmlFile file = getPsiFile();
    if (DescriptorUtil.isPluginXml(file)) {
      XmlTag rootTag = file.getRootTag();
      if (rootTag != null) {
        return new PluginDescriptorTreeElement(rootTag);
      }
    }

    // shouldn't happen; just for additional safety
    return super.getRoot();
  }

  @Override
  public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
    return false;
  }

  @Override
  public boolean isAlwaysLeaf(StructureViewTreeElement element) {
    return false;
  }
}
