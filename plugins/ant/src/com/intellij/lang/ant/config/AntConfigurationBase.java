// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config;

import com.intellij.lang.ant.config.impl.AntInstallation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.config.ExternalizablePropertyContainer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AntConfigurationBase extends AntConfiguration {

  private final ExternalizablePropertyContainer myProperties = new ExternalizablePropertyContainer();

  protected AntConfigurationBase(final Project project) {
    super(project);
  }

  public static AntConfigurationBase getInstance(@NotNull Project project) {
    return (AntConfigurationBase)AntConfiguration.getInstance(project);
  }

  public abstract boolean isFilterTargets();

  public abstract void setFilterTargets(final boolean value);

  public abstract List<ExecutionEvent> getEventsForTarget(final AntBuildTarget target);

  public abstract @Nullable AntBuildTarget getTargetForEvent(final ExecutionEvent event);

  public abstract void setTargetForEvent(final AntBuildFile buildFile, final String targetName, final ExecutionEvent event);

  public abstract void clearTargetForEvent(final ExecutionEvent event);

  public abstract boolean isAutoScrollToSource();

  public abstract void setAutoScrollToSource(final boolean value);

  public abstract AntInstallation getProjectDefaultAnt();

  public ExternalizablePropertyContainer getProperties() {
    return myProperties;
  }

  @ApiStatus.Internal
  public abstract void ensureInitialized();

  public abstract void setContextFile(@NotNull XmlFile file, @Nullable XmlFile context);

  public abstract @Nullable XmlFile getContextFile(@Nullable XmlFile file);

  public abstract @Nullable XmlFile getEffectiveContextFile(@Nullable XmlFile file);

  public abstract @Nullable AntBuildFileBase getAntBuildFile(@NotNull PsiFile file);

  @Override
  public abstract AntBuildFile[] getBuildFiles();
}
