// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Provides simple directory-oriented generators, which usually used in small IDEs, where there is only one module
 * {@link com.intellij.ide.util.projectWizard.AbstractNewProjectStep}
 * {@link com.intellij.ide.util.projectWizard.ProjectSettingsStepBase}
 * {@link com.intellij.ide.util.projectWizard.CustomStepProjectGenerator}
 * {@link HideableProjectGenerator}
 * 
 */
public interface DirectoryProjectGenerator<T> {
  default @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String getDescription() {
    return null;
  }

  default @Nullable String getHelpId() {
    return null;
  }

  /**
   * @deprecated unused
   */
  // to be removed in 2017.3
  @Deprecated(forRemoval = true)
  default boolean isPrimaryGenerator() {
    return true;
  }

  @NotNull
  @NlsContexts.Label
  String getName();

  default @NotNull NotNullLazyValue<ProjectGeneratorPeer<T>> createLazyPeer() {
    return NotNullLazyValue.lazy(this::createPeer);
  }

  /**
   * Creates new peer - new project settings and UI for them
   */
  default @NotNull ProjectGeneratorPeer<T> createPeer() {
    return new GeneratorPeerImpl<>();
  }

  /**
   * @return 16x16 icon or null, if no icon is available
   */
  @Nullable
  Icon getLogo();

  @RequiresEdt(generateAssertion = false)
  void generateProject(@NotNull Project project,
                       @NotNull VirtualFile baseDir,
                       @NotNull T settings,
                       @NotNull Module module);

  /**
   * Configure project when it's added to workspace as module.
   */
  default void configureModule(@NotNull Module module,
                               @NotNull VirtualFile baseDir,
                               @NotNull T settings) { }

  @NotNull
  ValidationResult validate(@NotNull String baseDirPath);
}
