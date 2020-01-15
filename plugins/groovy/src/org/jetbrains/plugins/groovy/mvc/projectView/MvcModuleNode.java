// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.mvc.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.AbstractModuleNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MvcModuleNode extends AbstractModuleNode {
  private final MvcToolWindowDescriptor myDescriptor;

  public MvcModuleNode(@NotNull Module module, ViewSettings viewSettings, MvcToolWindowDescriptor descriptor) {
    super(module.getProject(), module, viewSettings);
    myDescriptor = descriptor;
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode<?>> getChildren() {
    List<AbstractTreeNode<?>> nodesList = new ArrayList<>();
    Module module = getValue();
    ViewSettings viewSettings = getSettings();

    final VirtualFile root = myDescriptor.getFramework().findAppRoot(module);
    if (root == null) {
      return Collections.emptyList();
    }

    myDescriptor.fillModuleChildren(nodesList, module, viewSettings, root);

    return nodesList;
  }

  @Override
  public void update(@NotNull final PresentationData presentation) {
    super.update(presentation);

    final Module module = getValue();
    if (module == null || module.isDisposed()) {
      setValue(null);
      return;
    }
    // change default icon
    presentation.setIcon(myDescriptor.getModuleNodeIcon());
  }
}
