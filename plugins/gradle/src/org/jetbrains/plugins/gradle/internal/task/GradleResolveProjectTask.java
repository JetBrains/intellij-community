package org.jetbrains.plugins.gradle.internal.task;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.ExternalSystemFacadeManager;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProjectResolverImpl;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.externalSystem.service.project.change.ProjectStructureChangesModel;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/24/12 7:21 AM
 */
public class GradleResolveProjectTask extends AbstractGradleTask {

  private final AtomicReference<ProjectData> myGradleProject = new AtomicReference<ProjectData>();

  @NotNull private final String  myProjectPath;
  private final          boolean myResolveLibraries;

  public GradleResolveProjectTask(@Nullable Project project, @NotNull String projectPath, boolean resolveLibraries) {
    super(project, ExternalSystemTaskType.RESOLVE_PROJECT);
    myProjectPath = projectPath;
    myResolveLibraries = resolveLibraries;
  }

  protected void doExecute() throws Exception {
    final ExternalSystemFacadeManager manager = ServiceManager.getService(ExternalSystemFacadeManager.class);
    Project ideProject = getIdeProject();
    // TODO den implement
//    RemoteExternalSystemProjectResolverImpl resolver = manager.getFacade(ideProject).getResolver();
//    setState(GradleTaskState.IN_PROGRESS);
//
//    ProjectStructureChangesModel model = null;
//    if (ideProject != null && !ideProject.isDisposed()) {
//      model = ServiceManager.getService(ideProject, ProjectStructureChangesModel.class);
//    }
//    final ProjectData project;
//    try {
//      project = resolver.resolveProjectInfo(getId(), myProjectPath, myResolveLibraries);
//    }
//    catch (Exception e) {
//      if (model != null) {
//        model.clearChanges();
//      }
//      throw e;
//    }
//    
//    if (project == null) {
//      return;
//    }
//    myGradleProject.set(project);
//    setState(GradleTaskState.FINISHED);
//    
//    if (model != null) {
//      // This task may be called during the 'import from gradle' processing, hence, no project-level IoC is up.
//      // Model update is necessary for the correct tool window project structure diff showing but we don't have
//      // gradle tool window on this stage.
//      model.update(project);
//    }
  }

  @Nullable
  public ProjectData getGradleProject() {
    return myGradleProject.get();
  }

  @Override
  @NotNull
  protected String wrapProgressText(@NotNull String text) {
    // TODO den implement
    return "";
//    return ExternalSystemBundle.message("gradle.sync.progress.update.text", text);
  }
}
