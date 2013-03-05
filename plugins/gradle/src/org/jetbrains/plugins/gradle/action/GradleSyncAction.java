/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.autoimport.GradleUserProjectChange;
import org.jetbrains.plugins.gradle.config.GradleTextAttributes;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;
import org.jetbrains.plugins.gradle.manage.GradleEntityManageHelper;
import org.jetbrains.plugins.gradle.manage.GradleOutdatedLibraryManager;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 1/23/13 11:13 AM
 */
public class GradleSyncAction extends AbstractGradleSyncTreeNodeAction {

  public GradleSyncAction() {
    getTemplatePresentation().setText(GradleBundle.message("gradle.action.sync.text"));
    getTemplatePresentation().setDescription(GradleBundle.message("gradle.action.sync.description"));
  }

  @Override
  protected void filterNodes(@NotNull Collection<GradleProjectStructureNode<?>> nodes) {
    for (Iterator<GradleProjectStructureNode<?>> iterator = nodes.iterator(); iterator.hasNext(); ) {
      GradleProjectStructureNode<?> node = iterator.next();
      TextAttributesKey attributes = node.getDescriptor().getAttributes();
      if (!GradleTextAttributes.OUTDATED_ENTITY.equals(attributes) && node.getConflictChanges().isEmpty()) {
        iterator.remove();
      }
    }
  }

  @Override
  protected void doActionPerformed(@NotNull Collection<GradleProjectStructureNode<?>> nodes, @NotNull Project project, @NotNull Tree tree) {
    GradleOutdatedLibraryManager manager = ServiceManager.getService(project, GradleOutdatedLibraryManager.class);
    GradleEntityManageHelper helper = ServiceManager.getService(project, GradleEntityManageHelper.class);
    
    List<GradleProjectStructureNode<?>> outdatedLibraryNodes = ContainerUtilRt.newArrayList();
    Set<GradleProjectStructureChange> conflictChanges = ContainerUtilRt.newHashSet();
    for (GradleProjectStructureNode<?> node : nodes) {
      if (GradleTextAttributes.OUTDATED_ENTITY.equals(node.getDescriptor().getAttributes())) {
        outdatedLibraryNodes.add(node);
      }
      conflictChanges.addAll(node.getConflictChanges());
    }

    if (!outdatedLibraryNodes.isEmpty()) {
      manager.sync(nodes);
    }
    if (!conflictChanges.isEmpty()) {
      helper.eliminateChange(conflictChanges, Collections.<GradleUserProjectChange>emptySet(), true);
    }
  }
}
