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

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.xml.XmlStructureViewTreeModel;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.NodeProvider;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.util.Collection;
import java.util.Collections;

public class PluginDescriptorXmlStructureViewModel extends XmlStructureViewTreeModel {
  private static final String ACTION_ID = "PLUGIN_DESCRIPTOR_OUTLINE";

  public PluginDescriptorXmlStructureViewModel(@NotNull XmlFile file, @Nullable Editor editor) {
    super(file, editor);
  }


  @NotNull
  @Override
  public Collection<NodeProvider> getNodeProviders() {
    return Collections.singleton(new PluginDescriptorNodeProvider());
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


  private static class PluginDescriptorNodeProvider implements NodeProvider<PluginDescriptorTreeElement> {
    @NotNull
    @Override
    //TODO not really useful, review
    public ActionPresentation getPresentation() {
      return new ActionPresentationData(/*TODO externalize*/ "Plugin Descriptor Outline", null, AllIcons.Nodes.Plugin);
    }

    @NotNull
    @Override
    public String getName() {
      return ACTION_ID;
    }

    @NotNull
    @Override
    public Collection<PluginDescriptorTreeElement> provideNodes(@NotNull TreeElement node) {
      if (!(node instanceof PluginDescriptorTreeElement)) {
        return Collections.emptyList();
      }

      XmlTag tag = ((PluginDescriptorTreeElement)node).getElement();
      if (tag == null || !tag.isValid()) return Collections.emptyList();
      return ContainerUtil.map2List(tag.getSubTags(), PluginDescriptorTreeElement::new);
    }
  }
}
