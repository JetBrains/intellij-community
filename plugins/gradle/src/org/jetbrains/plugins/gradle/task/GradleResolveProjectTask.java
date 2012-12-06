package org.jetbrains.plugins.gradle.task;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.gradle.GradleProject;
import org.jetbrains.plugins.gradle.remote.GradleApiFacadeManager;
import org.jetbrains.plugins.gradle.remote.GradleProjectResolver;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureChangesModel;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/24/12 7:21 AM
 */
public class GradleResolveProjectTask extends AbstractGradleTask {
  
  private final AtomicReference<GradleProject> myGradleProject = new AtomicReference<GradleProject>();
  
  
  private final String  myProjectPath;
  private final boolean myResolveLibraries;
  
  public GradleResolveProjectTask(@Nullable Project project, @NotNull String projectPath, boolean resolveLibraries) {
    super(project, GradleTaskType.RESOLVE_PROJECT);
    myProjectPath = projectPath;
    myResolveLibraries = resolveLibraries;
  }

  protected void doExecute() throws Exception {
    final GradleApiFacadeManager manager = ServiceManager.getService(GradleApiFacadeManager.class);
    GradleProjectResolver resolver = manager.getFacade(getIntellijProject()).getResolver();
    setState(GradleTaskState.IN_PROGRESS);

    GradleProjectStructureChangesModel model = null;
    final Project intellijProject = getIntellijProject();
    if (intellijProject != null && !intellijProject.isDisposed()) {
      model = intellijProject.getComponent(GradleProjectStructureChangesModel.class);
    }
    final GradleProject project;
    try {
      project = resolver.resolveProjectInfo(getId(), myProjectPath, myResolveLibraries);
    }
    catch (Exception e) {
      if (model != null) {
        model.clearChanges();
      }
      throw e;
    }
    
    if (project == null) {
      return;
    }
    myGradleProject.set(project);
    setState(GradleTaskState.FINISHED);
    
    if (model != null) {
      // This task may be called during the 'import from gradle' processing, hence, no project-level IoC is up.
      // Model update is necessary for the correct tool window project structure diff showing but we don't have
      // gradle tool window on this stage.
      model.update(project);
    }
  }

  @Nullable
  public GradleProject getGradleProject() {
    return myGradleProject.get();
  }
}
