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
package com.intellij.openapi.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.util.messages.Topic;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Provides project management.
 */
public abstract class ProjectManager {
  public static final Topic<ProjectManagerListener> TOPIC = new Topic<>("Project open and close events", ProjectManagerListener.class);

  /**
   * Gets <code>ProjectManager</code> instance.
   *
   * @return <code>ProjectManager</code> instance
   */
  public static ProjectManager getInstance() {
    return ApplicationManager.getApplication().getComponent(ProjectManager.class);
  }

  /**
   * Adds global listener to all projects
   *
   * @param listener listener to add
   */
  public abstract void addProjectManagerListener(@NotNull ProjectManagerListener listener);
  public abstract void addProjectManagerListener(@NotNull ProjectManagerListener listener, @NotNull Disposable parentDisposable);

  /**
   * Removes global listener from all projects.
   *
   * @param listener listener to remove
   */
  public abstract void removeProjectManagerListener(@NotNull ProjectManagerListener listener);

  /**
   * Adds listener to the specified project.
   *
   * @param project  project to add listener to
   * @param listener listener to add
   */
  public abstract void addProjectManagerListener(@NotNull Project project, @NotNull ProjectManagerListener listener);

  /**
   * Removes listener from the specified project.
   *
   * @param project  project to remove listener from
   * @param listener listener to remove
   */
  public abstract void removeProjectManagerListener(@NotNull Project project, @NotNull ProjectManagerListener listener);

  /**
   * Returns the list of currently opened projects.
   *
   * @return the array of currently opened projects.
   */
  @NotNull
  public abstract Project[] getOpenProjects();

  /**
   * Returns the project which is used as a template for new projects. The template project
   * is always available, even when no other project is open. This {@link Project} instance is not
   * supposed to be used for anything except template settings storage.<p/>
   *
   * NB: default project can be lazy loaded
   *
   * @return the template project instance.
   */
  @NotNull
  public abstract Project getDefaultProject();

  /**
   * Loads and opens a project with the specified path. If the project file is from an older IDEA
   * version, prompts the user to convert it to the latest version. If the project file is from a
   * newer version, shows a message box telling the user that the load failed.
   *
   * @param filePath the .ipr file path
   * @return the opened project file, or null if the project failed to load because of version mismatch
   *         or because the project is already open.
   * @throws IOException          if the project file was not found or failed to read
   * @throws JDOMException        if the project file contained invalid XML
   * @throws InvalidDataException if the project file contained invalid data
   */
  @Nullable
  public abstract Project loadAndOpenProject(@NotNull String filePath) throws IOException, JDOMException, InvalidDataException;

  /**
   * Closes the specified project.
   *
   * @param project the project to close.
   * @return true if the project was closed successfully, false if the closing was disallowed by the close listeners.
   */
  public abstract boolean closeProject(@NotNull Project project);

  /**
   * Asynchronously reloads the specified project.
   *
   * @param project the project to reload.
   */
  public abstract void reloadProject(@NotNull Project project);

  /**
   * Create new project in given location.
   *
   * @param name project name
   * @param path project location
   *
   * @return newly crated project
   */
  @Nullable
  public abstract Project createProject(String name, String path);
}
