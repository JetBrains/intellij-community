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
package com.intellij.openapi.project.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;


@State(
  name = "ProjectDetails",
  storages = {
    @Storage(
        id="other",
        file = "$PROJECT_FILE$"
    )}
)
public class ProjectDetailsComponent implements PersistentStateComponent<ProjectDetailsComponent.State>, ProjectComponent {
  private final Project myProject;

  public void setProjectName(final String projectName) {
    myState.projectName = projectName;
  }

  public String getProjectName() {
    return myProject.isDefault() ? ((ProjectImpl)myProject).getDefaultName() : myState.projectName;
  }

  public static class State {
    public String projectName;
  }

  private State myState = new State();

  public ProjectDetailsComponent(Project project) {
    myProject = project;
    myState.projectName = ((ProjectImpl)myProject).getDefaultName();
  }

  public State getState() {
    if (!myProject.isDefault()) {
      return myState;
    }
    else {
      return null;
    }
  }

  public void loadState(final State state) {
    myState = state;
    if (!myProject.isDefault() && ProjectImpl.TEMPLATE_PROJECT_NAME.equals(myState.projectName) && myProject instanceof ProjectImpl){
      myState.projectName = ((ProjectImpl)myProject).getDefaultName();
    }
  }

  public void projectOpened() {

  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "ProjectDetails";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static ProjectDetailsComponent getInstance(Project project) {
    return project.getComponent(ProjectDetailsComponent.class);
  }
}
