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
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.adapter.TargetTypeProvider;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.idea.BasicIdeaProject;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.lang.reflect.Field;
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
    configureAdditionalTypes(controller);

    //outer conditional is needed to be compatible with 1.8
    final IdeaProject ideaProject = myIsPreviewMode ? controller.getModel(BasicIdeaProject.class) : controller.getModel(IdeaProject.class);
    if (ideaProject == null || ideaProject.getModules().isEmpty()) {
      return null;
    }

    AllModels allModels = new AllModels(ideaProject);
    addExtraProject(controller, allModels, null);
    for (IdeaModule module : ideaProject.getModules()) {
      addExtraProject(controller, allModels, module);
    }

    return allModels;
  }

  private static void configureAdditionalTypes(BuildController controller) {
    try {
      Field adapterField = controller.getClass().getDeclaredField("adapter");
      adapterField.setAccessible(true);
      ProtocolToModelAdapter adapter = (ProtocolToModelAdapter)adapterField.get(controller);

      Field typeProviderField = adapter.getClass().getDeclaredField("targetTypeProvider");
      typeProviderField.setAccessible(true);
      TargetTypeProvider typeProvider = (TargetTypeProvider)typeProviderField.get(adapter);

      Field targetTypesField = typeProvider.getClass().getDeclaredField("configuredTargetTypes");
      targetTypesField.setAccessible(true);
      //noinspection unchecked
      Map<String, Class<?>> targetTypes = (Map<String, Class<?>>)targetTypesField.get(typeProvider);

      targetTypes.put(ExternalProjectDependency.class.getCanonicalName(), ExternalProjectDependency.class);
      targetTypes.put(ExternalLibraryDependency.class.getCanonicalName(), ExternalLibraryDependency.class);
      targetTypes.put(FileCollectionDependency.class.getCanonicalName(), FileCollectionDependency.class);
      targetTypes.put(UnresolvedExternalDependency.class.getCanonicalName(), UnresolvedExternalDependency.class);
    }
    catch (Exception ignore) {
      // TODO handle error
    }
  }

  private void addExtraProject(@NotNull BuildController controller, @NotNull AllModels allModels, @Nullable IdeaModule model) {
    for (Class aClass : myExtraProjectModelClasses) {
      try {
        Object extraProject = controller.findModel(model, aClass);
        if (extraProject == null) continue;
        allModels.addExtraProject(extraProject, aClass, model);
      }
      catch (Exception e) {
        // do not fail project import in a preview mode
        if (!myIsPreviewMode) {
          throw new ExternalSystemException(e);
        }
      }
    }
  }

  public static class AllModels extends ModelsHolder<IdeaProject, IdeaModule> {

    public AllModels(@NotNull IdeaProject ideaProject) {
      super(ideaProject);
    }

    @NotNull
    public IdeaProject getIdeaProject() {
      return getRootModel();
    }

    @Nullable
    public BuildEnvironment getBuildEnvironment() {
      return getExtraProject(BuildEnvironment.class);
    }

    public void setBuildEnvironment(@Nullable BuildEnvironment buildEnvironment) {
      if (buildEnvironment != null) {
        addExtraProject(buildEnvironment, BuildEnvironment.class);
      }
    }

    @NotNull
    @Override
    protected String extractMapKey(Class modelClazz, @Nullable IdeaModule module) {
      return modelClazz.getName() + '@' +
             (module != null ? module.getGradleProject().getPath() : "root" + getRootModel().getName().hashCode());
    }
  }
}
