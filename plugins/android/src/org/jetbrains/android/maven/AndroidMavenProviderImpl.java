package org.jetbrains.android.maven;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidMavenProviderImpl implements AndroidMavenProvider {

  @Override
  public boolean isMavenizedModule(@NotNull Module module) {
    MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(module.getProject());
    return mavenProjectsManager != null ? mavenProjectsManager.isMavenizedModule(module) : null;
  }

  @NotNull
  public List<File> getMavenDependencyArtifactFiles(@NotNull Module module) {
    MavenProject mavenProject = MavenProjectsManager.getInstance(module.getProject()).findProject(module);
    List<File> result = new ArrayList<File>();
    if (mavenProject != null) {
      for (MavenArtifact depArtifact : mavenProject.getDependencies()) {
        if ("apksources".equals(depArtifact.getType())) {
          result.add(MavenArtifactUtil.getArtifactFile(mavenProject.getLocalRepository(), depArtifact.getMavenId()));
        }
      }
    }
    return result;
  }
}
