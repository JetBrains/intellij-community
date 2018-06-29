// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.idea.devkit.navigation.structure;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

public class PluginDescriptorTreeElement extends PsiTreeElementBase<XmlTag> implements DumbAware {
  private final boolean myIsRoot;
  private final boolean myIsTopLevelNode; // true for direct children of <idea-plugin>

  public PluginDescriptorTreeElement(@NotNull XmlTag psiElement, boolean isRootTag, boolean isTopLevelNode) {
    super(psiElement);
    myIsRoot = isRootTag;
    myIsTopLevelNode = isTopLevelNode;
  }


  @NotNull
  @Override
  public Collection<StructureViewTreeElement> getChildrenBase() {
    XmlTag tag = getElement();
    if (tag == null || !tag.isValid()) {
      return Collections.emptyList();
    }
    return ContainerUtil.map2List(tag.getSubTags(), psiElement -> new PluginDescriptorTreeElement(psiElement, false, myIsRoot));
  }

  @Nullable
  @Override
  public String getPresentableText() {
    XmlTag element = getElement();
    try {
      return PluginDescriptorStructureUtil.getTagDisplayText(element);
    } catch (IndexNotReadyException ignore) {
      return PluginDescriptorStructureUtil.safeGetTagDisplayText(element);
    }
  }

  @Override
  public String getLocationString() {
    try {
      return PluginDescriptorStructureUtil.getTagLocationString(getElement());
    } catch (IndexNotReadyException ignore) {
      return null;
    }
  }

  @Override
  public Icon getIcon(boolean open) {
    try {
      return PluginDescriptorStructureUtil.getTagIcon(getElement());
    } catch (IndexNotReadyException ignore) {
      return PluginDescriptorStructureUtil.DEFAULT_ICON;
    }
  }

  @Override
  public boolean isSearchInLocationString() {
    return true;
  }

  public boolean isTopLevelNode() {
    return myIsTopLevelNode;
  }
}
