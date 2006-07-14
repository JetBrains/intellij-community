package com.intellij.lang.ant.config;

import com.intellij.openapi.project.Project;
import com.intellij.util.config.ExternalizablePropertyContainer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AntConfigurationBase extends AntConfiguration {

  private final ExternalizablePropertyContainer myProperties = new ExternalizablePropertyContainer();

  protected AntConfigurationBase(final Project project) {
    super(project);
  }

  public static AntConfigurationBase getInstance(final Project project) {
    return (AntConfigurationBase)AntConfiguration.getInstance(project);
  }

  public abstract boolean isFilterTargets();

  public abstract void setFilterTargets(final boolean value);

  public abstract List<ExecutionEvent> getEventsForTarget(final AntBuildTarget target);

  @Nullable
  public abstract AntBuildTarget getTargetForEvent(final ExecutionEvent event);

  public abstract void setTargetForEvent(final AntBuildFile buildFile, final String targetName, final ExecutionEvent event);

  public abstract void clearTargetForEvent(final ExecutionEvent event);

  public abstract boolean isAutoScrollToSource();

  public abstract void setAutoScrollToSource(final boolean value);

  public ExternalizablePropertyContainer getProperties() {
    return myProperties;
  }
}
