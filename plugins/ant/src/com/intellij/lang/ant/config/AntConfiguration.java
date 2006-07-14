package com.intellij.lang.ant.config;

import com.intellij.lang.ant.config.impl.AntReference;
import com.intellij.lang.ant.config.impl.NoPsiFileException;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.ExternalizablePropertyContainer;
import com.intellij.util.config.ValueProperty;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AntConfiguration implements ProjectComponent, ModificationTracker {

  private static final Map<Project, AntConfiguration> CONFIGURATIONS = new HashMap<Project, AntConfiguration>();

  public static final ValueProperty<AntReference> DEFAULT_ANT = new ValueProperty<AntReference>("defaultAnt", AntReference.BUNDLED_ANT);
  public static final ValueProperty<AntConfiguration> INSTANCE = new ValueProperty<AntConfiguration>("$instance", null);
  public static final AbstractProperty<String> DEFAULT_JDK_NAME = new AbstractProperty<String>() {
    public String getName() {
      return "$defaultJDKName";
    }

    @Nullable
    public String getDefault(final AbstractPropertyContainer container) {
      return get(container);
    }

    @Nullable
    public String get(final AbstractPropertyContainer container) {
      if (!container.hasProperty(this)) return null;
      AntConfiguration antConfiguration = INSTANCE.get(container);
      return ProjectRootManager.getInstance(antConfiguration.myProject).getProjectJdkName();
    }

    public String copy(final String jdkName) {
      return jdkName;
    }
  };

  private final ExternalizablePropertyContainer myProperties = new ExternalizablePropertyContainer();
  protected final Project myProject;

  protected AntConfiguration(final Project project) {
    CONFIGURATIONS.put(project, this);
    myProperties.registerProperty(DEFAULT_ANT, AntReference.EXTERNALIZER);
    myProperties.rememberKey(INSTANCE);
    myProperties.rememberKey(DEFAULT_JDK_NAME);
    INSTANCE.set(myProperties, this);
    myProject = project;
  }

  public static AntConfiguration getInstance(final Project project) {
    return CONFIGURATIONS.get(project);
  }

  public ExternalizablePropertyContainer getProperties() {
    return myProperties;
  }

  public abstract List<AntBuildFile> getBuildFiles();

  public abstract AntBuildFile addBuildFile(final VirtualFile file) throws NoPsiFileException;

  public abstract void removeBuildFile(final AntBuildFile file);

  public abstract void addAntConfigurationListener(final AntConfigurationListener listener);

  public abstract void removeAntConfigurationListener(final AntConfigurationListener listener);

  public abstract boolean isFilterTargets();

  public abstract void setFilterTargets(final boolean value);

  public abstract AntBuildTarget[] getMetaTargets(final AntBuildFile buildFile);

  public abstract List<ExecutionEvent> getEventsForTarget(final AntBuildTarget target);

  @Nullable
  public abstract AntBuildTarget getTargetForEvent(final ExecutionEvent event);

  public abstract void setTargetForEvent(final AntBuildFile buildFile, final String targetName, final ExecutionEvent event);

  public abstract void clearTargetForEvent(final ExecutionEvent event);

  public abstract void updateBuildFile(final AntBuildFile buildFile);

  public abstract boolean isAutoScrollToSource();

  public abstract void setAutoScrollToSource(final boolean value);

  @Nullable
  public abstract AntBuildModel getModelIfRegistered(final AntBuildFile buildFile);

  public abstract AntBuildModel getModel(final AntBuildFile buildFile);
}
