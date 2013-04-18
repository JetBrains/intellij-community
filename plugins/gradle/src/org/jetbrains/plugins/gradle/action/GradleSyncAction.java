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
import com.intellij.openapi.externalSystem.model.project.change.ExternalProjectStructureChange;
import com.intellij.openapi.externalSystem.service.project.manage.EntityManageHelper;
import com.intellij.openapi.externalSystem.service.project.manage.OutdatedLibraryService;
import com.intellij.openapi.externalSystem.settings.ExternalSystemTextAttributes;
import com.intellij.openapi.externalSystem.ui.ProjectStructureNode;
import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 1/23/13 11:13 AM
 */
public class GradleSyncAction extends AbstractGradleSyncTreeNodeAction {

  public GradleSyncAction() {
    // TODO den implement
//    getTemplatePresentation().setText(ExternalSystemBundle.message("gradle.action.sync.text"));
//    getTemplatePresentation().setDescription(ExternalSystemBundle.message("gradle.action.sync.description"));
  }

  @Override
  protected void filterNodes(@NotNull Collection<ProjectStructureNode<?>> nodes) {
    for (Iterator<ProjectStructureNode<?>> iterator = nodes.iterator(); iterator.hasNext(); ) {
      ProjectStructureNode<?> node = iterator.next();
      TextAttributesKey attributes = node.getDescriptor().getAttributes();
      if (!ExternalSystemTextAttributes.OUTDATED_ENTITY.equals(attributes) && node.getConflictChanges().isEmpty()) {
        iterator.remove();
      }
    }
  }

  @Override
  protected void doActionPerformed(@NotNull Collection<ProjectStructureNode<?>> nodes, @NotNull Project project, @NotNull Tree tree) {
    OutdatedLibraryService manager = ServiceManager.getService(project, OutdatedLibraryService.class);
    EntityManageHelper helper = ServiceManager.getService(project, EntityManageHelper.class);
    
    List<ProjectStructureNode<?>> outdatedLibraryNodes = ContainerUtilRt.newArrayList();
    Set<ExternalProjectStructureChange> conflictChanges = ContainerUtilRt.newHashSet();
    for (ProjectStructureNode<?> node : nodes) {
      if (ExternalSystemTextAttributes.OUTDATED_ENTITY.equals(node.getDescriptor().getAttributes())) {
        outdatedLibraryNodes.add(node);
      }
      conflictChanges.addAll(node.getConflictChanges());
    }

    // TODO den implement
//    if (!outdatedLibraryNodes.isEmpty()) {
//      manager.sync(nodes);
//    }
//    if (!conflictChanges.isEmpty()) {
//      helper.eliminateChange(conflictChanges, Collections.<UserProjectChange>emptySet(), true);
//    }
  }
}
