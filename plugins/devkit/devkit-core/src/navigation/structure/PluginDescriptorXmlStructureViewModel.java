// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.idea.devkit.navigation.structure;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.xml.XmlStructureViewTreeModel;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
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
        return new PluginDescriptorTreeElement(rootTag, true, true);
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
    return new Sorter[] {
      new Sorter() {
        @Override
        public Comparator getComparator() {
          return (o1, o2) -> {
            if (o1 instanceof PluginDescriptorTreeElement && o2 instanceof PluginDescriptorTreeElement) {
              PluginDescriptorTreeElement e1 = (PluginDescriptorTreeElement)o1;
              PluginDescriptorTreeElement e2 = (PluginDescriptorTreeElement)o2;
              if (e1.isTopLevelNode() && e2.isTopLevelNode()) {
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

  @NotNull
  @Override
  protected Class[] getSuitableClasses() {
    return new Class[]{XmlTag.class};
  }
}
