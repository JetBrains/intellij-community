/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
