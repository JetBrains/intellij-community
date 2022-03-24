// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.Topic;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Provides project management.
 */
@ApiStatus.NonExtendable
public abstract class ProjectManager {
  @Topic.AppLevel
  public static final Topic<ProjectManagerListener> TOPIC = new Topic<>(ProjectManagerListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true);

  /**
   * @return {@code ProjectManager} instance
   */
  public static ProjectManager getInstance() {
    return ApplicationManager.getApplication().getService(ProjectManager.class);
  }

  public static @Nullable ProjectManager getInstanceIfCreated() {
    return ApplicationManager.getApplication().getServiceIfCreated(ProjectManager.class);
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
  public abstract @NotNull Project @NotNull [] getOpenProjects();

  /**
   * Returns the project which is used as a template for new projects. The template project
   * is always available, even when no other project is open. This {@link Project} instance is not
   * supposed to be used for anything except template settings storage.<p/>
   *
   * NB: default project can be lazy loaded
   *
   * @return the template project instance.
   */
  public abstract @NotNull Project getDefaultProject();

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
  public abstract @Nullable Project loadAndOpenProject(@NotNull String filePath) throws IOException, JDOMException;

  /**
   * Save, close and dispose project. Please note that only the project will be saved, but not the application.
   * @return true on success
   */
  public abstract boolean closeAndDispose(@NotNull Project project);

  /**
   * @deprecated Use {@link #closeAndDispose}
   */
  @Deprecated
  public abstract boolean closeProject(@NotNull Project project);

  /**
   * Asynchronously reloads the specified project.
   *
   * @param project the project to reload.
   */
  public abstract void reloadProject(@NotNull Project project);

  /**
   * @deprecated Use {@link com.intellij.openapi.project.ex.ProjectManagerEx#newProject(Path, com.intellij.ide.impl.OpenProjectTask)}
   */
  @Deprecated(forRemoval = true)
  public abstract @Nullable Project createProject(@Nullable String name, @NotNull String path);

  public @Nullable Project findOpenProjectByHash(@Nullable String locationHash) {
    return null;
  }
}
