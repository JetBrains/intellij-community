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
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.xml.XmlStructureViewTreeModel;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.util.Comparator;

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

  @NotNull
  @Override
  public Sorter[] getSorters() {
    TreeElement[] topLevelNodes = getRoot().getChildren();
    return new Sorter[] {
      new Sorter() {
        @Override
        public Comparator getComparator() {
          return (o1, o2) -> {
            if (o1 instanceof TreeElement && o2 instanceof TreeElement) {
              TreeElement e1 = (TreeElement)o1;
              TreeElement e2 = (TreeElement)o2;
              if (ArrayUtil.contains(e1, topLevelNodes) && ArrayUtil.contains(e2, topLevelNodes)) {
                return 0;
              }
            }

            //noinspection unchecked
            return ALPHA_SORTER.getComparator().compare(o1, o2);
          };
        }

        @Override
        public boolean isVisible() {
          return ALPHA_SORTER.isVisible();
        }

        @NotNull
        @Override
        public ActionPresentation getPresentation() {
          return new ActionPresentationData(DevKitBundle.message("structure.sort.alphabetically.in.groups"),
                                            DevKitBundle.message("structure.sort.alphabetically.in.groups"),
                                            AllIcons.ObjectBrowser.Sorted);
        }

        @NotNull
        @Override
        public String getName() {
          return "PluginDescriptorStructureAlphaSorter";
        }
      }
    };
  }
}
