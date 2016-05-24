/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.execution.Location;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class TestCaseAsRelatedFileProvider extends GotoRelatedProvider {
  @NotNull
  @Override
  public List<? extends GotoRelatedItem> getItems(@NotNull DataContext context) {
    final Editor editor = context.getData(CommonDataKeys.EDITOR);
    final Project project = context.getData(CommonDataKeys.PROJECT);
    final VirtualFile file = context.getData(CommonDataKeys.VIRTUAL_FILE);
    if (editor == null || file == null || project == null) {
      return Collections.emptyList();
    }

    final List<Location> locations = TestLocationDataRule.collectRelativeLocations(project, file);
    if (locations.isEmpty()) {
      return Collections.emptyList();
    }
    
    return ContainerUtil.map(locations, location -> new GotoRelatedItem(location.getPsiElement()));
  }
}
