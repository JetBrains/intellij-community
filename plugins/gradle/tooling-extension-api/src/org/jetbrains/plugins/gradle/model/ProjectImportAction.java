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
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.gradle.GradleBuild;
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

  private final Set<Class> myExtraProjectModelClasses = new LinkedHashSet<Class>();
  private final boolean myIsPreviewMode;
  private final boolean myIsGradleProjectDirSupported;
  private final boolean myIsCompositeBuildsSupported;

  public ProjectImportAction(boolean isPreviewMode) {
    this(isPreviewMode, false, false);
  }

  public ProjectImportAction(boolean isPreviewMode, boolean isGradleProjectDirSupported, boolean isCompositeBuildsSupported) {
    myIsPreviewMode = isPreviewMode;
    myIsGradleProjectDirSupported= isGradleProjectDirSupported;
    myIsCompositeBuildsSupported = isCompositeBuildsSupported;
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
    allModels.setGradleProjectDirSupported(myIsGradleProjectDirSupported);
    BuildEnvironment buildEnvironment = controller.findModel(BuildEnvironment.class);
    allModels.setBuildEnvironment(buildEnvironment);
    addExtraProject(controller, allModels, null);
    for (IdeaModule module : ideaProject.getModules()) {
      addExtraProject(controller, allModels, module);
    }

    if (myIsCompositeBuildsSupported) {
      GradleBuild gradleBuild = controller.getModel(GradleBuild.class);
      for (GradleBuild build : gradleBuild.getIncludedBuilds()) {
        IdeaProject ideaIncludedProject = controller.findModel(build, IdeaProject.class);
        allModels.getIncludedBuilds().add(ideaIncludedProject);
        for (IdeaModule module : ideaIncludedProject.getModules()) {
          addExtraProject(controller, allModels, module);
        }
      }
    }

    return allModels;
  }

  private static void configureAdditionalTypes(BuildController controller) {
    try {
      Field adapterField;
      try {
        adapterField = controller.getClass().getDeclaredField("adapter");
      }
      catch (NoSuchFieldException e) {
        // since v.4.4 there is a BuildControllerWithoutParameterSupport can be used
        Field delegate = controller.getClass().getDeclaredField("delegate");
        delegate.setAccessible(true);
        Object wrappedController = delegate.get(controller);
        adapterField = wrappedController.getClass().getDeclaredField("adapter");
        controller = (BuildController)wrappedController;
      }
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
        allModels.addExtraProject(extraProject, aClass, model != null ? model.getGradleProject() : null);
      }
      catch (Exception e) {
        // do not fail project import in a preview mode
        if (!myIsPreviewMode) {
          throw new ExternalSystemException(e);
        }
      }
    }
  }

  public static class AllModels extends ModelsHolder<IdeaProject, GradleProject> {

    private List<IdeaProject> includedBuilds = new ArrayList<IdeaProject>();
    private boolean isGradleProjectDirSupported;

    public AllModels(@NotNull IdeaProject ideaProject) {
      super(ideaProject);
    }

    @NotNull
    public IdeaProject getIdeaProject() {
      return getRootModel();
    }


    public List<IdeaProject> getIncludedBuilds() {
      return includedBuilds;
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

    public void setGradleProjectDirSupported(boolean gradleProjectDirSupported) {
      isGradleProjectDirSupported = gradleProjectDirSupported;
    }

    @Nullable
    public <T> T getExtraProject(@Nullable IdeaModule model, Class<T> modelClazz) {
      return super.getExtraProject(model != null ? model.getGradleProject() : null, modelClazz);
    }

    public void addExtraProject(@NotNull Object project, @NotNull Class modelClazz, @Nullable IdeaModule subPropject) {
      super.addExtraProject(project, modelClazz, subPropject != null ? subPropject.getGradleProject() : null);
    }

    @NotNull
    @Override
    protected String extractMapKey(Class modelClazz, @Nullable GradleProject gradleProject) {
      if (gradleProject != null) {
        String id = isGradleProjectDirSupported ?
                    gradleProject.getProjectDirectory().getPath() :
                    gradleProject.getPath();
        return modelClazz.getName() + '@' + id;
      }
      else {
        return modelClazz.getName() + '@' + ("root" + getRootModel().getName().hashCode());
      }
    }
  }
}
