// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.Topic;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;

/**
 * Provides project management.
 */
public abstract class ProjectManager {
  public static final Topic<ProjectManagerListener> TOPIC = new Topic<>("Project open and close events", ProjectManagerListener.class);

  /**
   * @return {@code ProjectManager} instance
   */
  public static ProjectManager getInstance() {
    return ApplicationManager.getApplication().getComponent(ProjectManager.class);
  }

  /**
   * @deprecated Use {@link #TOPIC} instead
   */
  @Deprecated
  public abstract void addProjectManagerListener(@NotNull ProjectManagerListener listener);

  public abstract void addProjectManagerListener(@NotNull VetoableProjectManagerListener listener);

  /**
   * @deprecated Use {@link #TOPIC} instead
   */
  @Deprecated
  public abstract void addProjectManagerListener(@NotNull ProjectManagerListener listener, @NotNull Disposable parentDisposable);

  /**
   * @deprecated Use {@link #TOPIC} instead
   */
  @Deprecated
  public abstract void removeProjectManagerListener(@NotNull ProjectManagerListener listener);

  public abstract void removeProjectManagerListener(@NotNull VetoableProjectManagerListener listener);

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
   * {@link Project#isDisposed()} must be checked for each project before use (if the whole operation is not under read action).
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
   */
  @Nullable
  public abstract Project loadAndOpenProject(@NotNull String filePath) throws IOException, JDOMException;

  @ApiStatus.Experimental
  @TestOnly
  public abstract Project loadAndOpenProject(@NotNull File file) throws IOException, JDOMException;

  /**
   * Closes the specified project, but does not dispose it.
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
  @SuppressWarnings("unused")
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
  public abstract Project createProject(@Nullable String name, @NotNull String path);
}
