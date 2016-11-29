/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/**
 * @author peter
 */

public class MvcModuleNode extends AbstractModuleNode {
  private final MvcToolWindowDescriptor myDescriptor;

  public MvcModuleNode(@NotNull final Module module, final ViewSettings viewSettings, MvcToolWindowDescriptor descriptor) {
    super(module.getProject(), module, viewSettings);
    myDescriptor = descriptor;
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    final List<AbstractTreeNode> nodesList = new ArrayList<>();

    final Module module = getValue();

    final ViewSettings viewSettings = getSettings();

    final VirtualFile root = myDescriptor.getFramework().findAppRoot(module);
    if (root == null) {
      return Collections.emptyList();
    }

    myDescriptor.fillModuleChildren(nodesList, module, viewSettings, root);

    return nodesList;
  }

  @Override
  public void update(final PresentationData presentation) {
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
