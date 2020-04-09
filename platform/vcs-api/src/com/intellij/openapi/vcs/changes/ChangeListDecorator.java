/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Allows to modify the painting of changelists in the Changes view.
 * <p>
 * Classes implementing this interface can be registered as project components.
 */
public interface ChangeListDecorator {
  ProjectExtensionPointName<ChangeListDecorator> EP_NAME = new ProjectExtensionPointName<>("com.intellij.vcs.changeListDecorator");

  @NotNull
  static List<ChangeListDecorator> getDecorators(@NotNull Project project) {
    if (project.isDisposed()) return Collections.emptyList();
    //noinspection deprecation
    return ContainerUtil.concat(EP_NAME.getExtensions(project),
                                project.getComponentInstancesOfType(ChangeListDecorator.class));
  }

  void decorateChangeList(@NotNull LocalChangeList changeList, @NotNull ColoredTreeCellRenderer cellRenderer,
                          boolean selected, boolean expanded, boolean hasFocus);
}
