package org.jetbrains.plugins.gradle.internal.task;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.gradle.GradleProject;
import org.jetbrains.plugins.gradle.remote.GradleApiFacadeManager;
import org.jetbrains.plugins.gradle.remote.GradleProjectResolver;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureChangesModel;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/24/12 7:21 AM
 */
public class GradleResolveProjectTask extends AbstractGradleTask {
  
  private final AtomicReference<GradleProject> myGradleProject = new AtomicReference<GradleProject>();
  
  @NotNull private final String  myProjectPath;
  private final boolean myResolveLibraries;
  
  public GradleResolveProjectTask(@Nullable Project project, @NotNull String projectPath, boolean resolveLibraries) {
    super(project, GradleTaskType.RESOLVE_PROJECT);
    myProjectPath = projectPath;
    myResolveLibraries = resolveLibraries;
  }

  protected void doExecute() throws Exception {
    final GradleApiFacadeManager manager = ServiceManager.getService(GradleApiFacadeManager.class);
    Project ideProject = getIdeProject();
    GradleProjectResolver resolver = manager.getFacade(ideProject).getResolver();
    setState(GradleTaskState.IN_PROGRESS);

    GradleProjectStructureChangesModel model = null;
    if (ideProject != null && !ideProject.isDisposed()) {
      model = ServiceManager.getService(ideProject, GradleProjectStructureChangesModel.class);
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

  @Override
  @NotNull
  protected String wrapProgressText(@NotNull String text) {
    return GradleBundle.message("gradle.sync.progress.update.text", text);
  }
}
