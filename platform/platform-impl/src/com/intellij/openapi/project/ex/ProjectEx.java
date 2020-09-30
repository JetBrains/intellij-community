// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public interface ProjectEx extends Project {
  String NAME_FILE = ".name";

  /**
   * Consider using only and only if {@link com.intellij.configurationStore.SettingsSavingComponent} is not possible to use.
   */
  @ApiStatus.Internal
  interface ProjectSaved {
    @Topic.ProjectLevel
    Topic<ProjectSaved> TOPIC = new Topic<>("SaveProjectTopic", ProjectSaved.class, Topic.BroadcastDirection.NONE);

    /**
     * Not called in EDT.
     */
    default void duringSave(@NotNull Project project) {
    }
  }

  void setProjectName(@NotNull String name);

  @TestOnly
  default boolean isLight() {
    return false;
  }

  /**
   * {@link Disposable} that will be disposed right after container started to be disposed.
   * Use it to dispose something that need to be disposed very early, e.g. {@link com.intellij.util.Alarm}.
   * Or, only and only in unit test mode, if you need to publish something to message bus during dispose.<p/>
   *
   * In unit test mode light project is not disposed, but this disposable is disposed for each test.
   * So, you don't need to have another disposable and can use this one instead.<p/>
   *
   * Dependent {@link Disposable#dispose} may be called in any thread.
   * Implementation of {@link Disposable#dispose} must be self-contained and isolated (getting services is forbidden, publishing to message bus is allowed only in tests).
   */
  @NotNull
  @ApiStatus.Experimental
  @ApiStatus.Internal
  Disposable getEarlyDisposable();

  @TestOnly
  default @Nullable String getCreationTrace() {
    return null;
  }
}
