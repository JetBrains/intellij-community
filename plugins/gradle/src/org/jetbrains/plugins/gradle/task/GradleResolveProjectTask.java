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
  
  private final Project myIntellijProject;
  private final String  myProjectPath;
  private final boolean myResolveLibraries;
  
  public GradleResolveProjectTask(@Nullable Project project, @NotNull String projectPath, boolean resolveLibraries) {
    super(GradleTaskType.RESOLVE_PROJECT);
    myIntellijProject = project;
    myProjectPath = projectPath;
    myResolveLibraries = resolveLibraries;
  }

  protected void doExecute() throws Exception {
    final GradleApiFacadeManager manager = ServiceManager.getService(GradleApiFacadeManager.class);
    GradleProjectResolver resolver = manager.getFacade().getResolver();
    setState(GradleTaskState.IN_PROGRESS);
    final GradleProject project = resolver.resolveProjectInfo(getId(), myProjectPath, myResolveLibraries);
    myGradleProject.set(project);
    setState(GradleTaskState.FINISHED);
    if (myIntellijProject == null) {
      return;
    }
    final GradleProjectStructureChangesModel model = myIntellijProject.getComponent(GradleProjectStructureChangesModel.class);
    if (model != null) {
      // This task may be called during the 'import from gradle' processing, hence, no project-level IoC is up.
      // Model update is necessary for the correct tool window project structure diff showing but we don't have
      // gradle tool window on this stage.
      model.update(project);
    }
  }

  @Nullable
  public GradleProject getProject() {
    return myGradleProject.get();
  }
}
