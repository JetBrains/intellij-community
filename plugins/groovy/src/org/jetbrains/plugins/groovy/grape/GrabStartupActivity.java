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
package org.jetbrains.plugins.groovy.grape;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.AdditionalIndexableFileSet;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexProjectHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.resolve.GrabService;

public class GrabStartupActivity implements StartupActivity {

  @Override
  public void runActivity(@NotNull Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    GrabService grabService = GrabService.getInstance(project);
    grabService.scheduleUpdate(GlobalSearchScope.allScope(project)); // not schedule

    //AdditionalIndexableFileSet fileSet = new AdditionalIndexableFileSet(project, new GrabRootsProvider()) {
    //  @Override
    //  public boolean isInSet(@NotNull VirtualFile file) {
    //    System.out.println(file.getPath());
    //    return super.isInSet(file);
    //  }
    //};
    //FileBasedIndex index = FileBasedIndex.getInstance();
    //index.registerIndexableSet(fileSet, project);
    //ProjectManager.getInstance().addProjectManagerListener(project, new ProjectManagerAdapter() {
    //  private boolean removed;
    //  @Override
    //  public void projectClosing(Project project1) {
    //    if (!removed) {
    //      removed = true;
    //      index.removeIndexableSet(fileSet);
    //    }
    //  }
    //});
  }
}
