/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.model;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.idea.BasicIdeaProject;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.*;

/**
 * @author Vladislav.Soroka
 * @since 10/14/13
 */
public class ProjectImportAction implements BuildAction<ProjectImportAction.AllModels>, Serializable {

  private final Set<Class> myExtraProjectModelClasses = new HashSet<Class>();
  private final boolean myIsPreviewMode;

  public ProjectImportAction(boolean isPreviewMode) {
    myIsPreviewMode = isPreviewMode;
  }

  public void addExtraProjectModelClasses(@NotNull Set<Class> projectModelClasses) {
    myExtraProjectModelClasses.addAll(projectModelClasses);
  }

  @Nullable
  @Override
  public AllModels execute(final BuildController controller) {
    Class<? extends IdeaProject> aClass1 = myIsPreviewMode ? BasicIdeaProject.class : IdeaProject.class;
    final IdeaProject ideaProject = controller.getModel(aClass1);
    if (ideaProject == null || ideaProject.getModules().isEmpty()) {
      return null;
    }

    AllModels allModels = new AllModels(ideaProject);

    // TODO ask gradle guys why there is always null got for BuildEnvironment model
    //allModels.setBuildEnvironment(controller.findModel(BuildEnvironment.class));

    for (IdeaModule module : ideaProject.getModules()) {
      for (Class aClass : myExtraProjectModelClasses) {
        try {
          Object extraProject = controller.findModel(module, aClass);
          if (extraProject == null) continue;
          allModels.addExtraProject(extraProject, aClass, module);
        }
        catch (Exception e) {
          // do not fail project import in a preview mode
          if (!myIsPreviewMode) {
            throw new ExternalSystemException(e);
          }
        }
      }
    }

    return allModels;
  }

  public static class AllModels implements Serializable {
    @NotNull private final Map<String, Object> projectsByPath = new HashMap<String, Object>();
    @NotNull private final IdeaProject myIdeaProject;
    @Nullable private BuildEnvironment myBuildEnvironment;

    public AllModels(@NotNull IdeaProject project) {
      myIdeaProject = project;
    }

    @NotNull
    public IdeaProject getIdeaProject() {
      return myIdeaProject;
    }

    @Nullable
    public BuildEnvironment getBuildEnvironment() {
      return myBuildEnvironment;
    }

    public void setBuildEnvironment(@Nullable BuildEnvironment buildEnvironment) {
      myBuildEnvironment = buildEnvironment;
    }


    @Nullable
    public <T> T getExtraProject(@NotNull IdeaModule module, Class<T> modelClazz) {
      Object extraProject = projectsByPath.get(extractMapKey(modelClazz, module));
      if (modelClazz.isInstance(extraProject)) {
        //noinspection unchecked
        return (T)extraProject;
      }
      return null;
    }

    /**
     * Return collection path of modules provides the model
     *
     * @param modelClazz extra project model
     * @return modules path collection
     */
    @NotNull
    public Collection<String> findModulesWithModel(@NotNull Class modelClazz) {
      List<String> modules = new ArrayList<String>();
      for (Map.Entry<String, Object> set : projectsByPath.entrySet()) {
        if (modelClazz.isInstance(set.getValue())) {
          modules.add(extractModulePath(modelClazz, set.getKey()));
        }
      }
      return modules;
    }

    public void addExtraProject(@NotNull Object project, @NotNull Class modelClazz, @NotNull IdeaModule module) {
      projectsByPath.put(extractMapKey(modelClazz, module), project);
    }

    @NotNull
    private static String extractMapKey(Class modelClazz, @NotNull IdeaModule module) {
      return modelClazz.getName() + '@' + module.getGradleProject().getPath();
    }

    @NotNull
    private static String extractModulePath(Class modelClazz, String key) {
      return key.replaceFirst(modelClazz.getName() + '@', "");
    }

    @Override
    public String toString() {
      return "AllModels{" +
             "projectsByPath=" + projectsByPath +
             ", myIdeaProject=" + myIdeaProject +
             '}';
    }
  }
}
