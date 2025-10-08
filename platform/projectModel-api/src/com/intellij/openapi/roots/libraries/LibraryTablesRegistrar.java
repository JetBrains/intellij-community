// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.NonExtendable
public abstract class LibraryTablesRegistrar {
  public static final @NonNls String PROJECT_LEVEL = "project";
  public static final @NonNls String APPLICATION_LEVEL = "application";

  public static LibraryTablesRegistrar getInstance() {
    return ApplicationManager.getApplication().getService(LibraryTablesRegistrar.class);
  }

  /**
   * Returns the application-level (aka "global") library table for the eel environment of the current process.
   * <p>
   * Now application-level libraries are partitioned per eel environment (similar to SDKs).
   * The no-argument variant uses the process' current environment; callers that
   * need a specific environment tied to a project should use {@link #getGlobalLibraryTable(Project)}.
   *
   * @return the application-level library table for the current process eel environment.
   */
  public abstract @NotNull LibraryTable getLibraryTable();

  /**
   * Returns the application-level (aka "global") library table selected by the eel environment
   * associated with the given {@code project}. The {@code project} parameter is used only to
   * determine the environment (via the project's already-resolved {@code EelMachine}); the returned table
   * is NOT a project-level table and may be used across projects that share the same environment.
   * <p>
   * UI location: 'Project Structure' | 'Platform Settings' | 'Global Libraries'.
   * <p>
   * Environment selection notes:
   * <ul>
   *   <li>Projects belonging to different eel environments see different sets of application-level libraries.</li>
   *   <li>This method does not perform any suspend/async operations; it relies on the environment
   *       already bound to the provided {@code project}.</li>
   * </ul>
   *
   * @param project a non-disposed project whose bound eel environment is used to choose the
   *                application-level library table.
   * @return the environment-specific application-level library table visible as "Global Libraries".
   * @see #getLibraryTable()
   * @see #getLibraryTable(Project)
   */
  @ApiStatus.Experimental
  public abstract @NotNull LibraryTable getGlobalLibraryTable(@NotNull Project project);

  /**
   * Returns the table containing project-level libraries for given {@code project}. These libraries are shown in 'Project Structure'
   * | 'Project Settings' | 'Libraries' and may be added to dependencies of the corresponding project's modules only.
   */
  public abstract @NotNull LibraryTable getLibraryTable(@NotNull Project project);

  /**
   * Returns the standard or a custom library table registered via {@link CustomLibraryTableDescription}.
   */
  public abstract @Nullable LibraryTable getLibraryTableByLevel(@NonNls String level, @NotNull Project project);

  /**
   * Returns a custom library table registered via {@link CustomLibraryTableDescription}.
   */
  public abstract @Nullable LibraryTable getCustomLibraryTableByLevel(@NonNls String level);

  public abstract @NotNull List<LibraryTable> getCustomLibraryTables();
}