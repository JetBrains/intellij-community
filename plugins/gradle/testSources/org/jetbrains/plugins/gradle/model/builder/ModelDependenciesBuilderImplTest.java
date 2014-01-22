/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.model.builder;

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaModuleDependency;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleDependencyScope;
import org.jetbrains.plugins.gradle.model.ProjectDependenciesModel;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Vladislav.Soroka
 * @since 11/29/13
 */
public class ModelDependenciesBuilderImplTest extends AbstractModelBuilderTest {

  @Test
  public void testDefaultDependenciesModel() throws Exception {
    ModelDependenciesBuilderImpl dependenciesBuilder = new ModelDependenciesBuilderImpl();
    assertTrue(dependenciesBuilder.canBuild("org.jetbrains.plugins.gradle.model.ProjectDependenciesModel"));

    DomainObjectSet<? extends IdeaModule> ideaModules = allModels.getIdeaProject().getModules();

    final int modulesSize = 3;
    assertEquals(modulesSize, ideaModules.size());

    for (IdeaModule ideaModule : ideaModules) {
      if (ideaModule.getName().equals("dependencyProject") ||
          ideaModule.getName().equals("testDefaultDependenciesModel")) {
        DomainObjectSet<? extends IdeaDependency> dependencies = ideaModule.getDependencies();
        assertTrue((dependencies.isEmpty()));
      }
      else if (ideaModule.getName().equals("dependentProject")) {
        DomainObjectSet<? extends IdeaDependency> dependencies = ideaModule.getDependencies();
        assertEquals(1, dependencies.size());
        assertTrue(dependencies.getAt(0) instanceof IdeaModuleDependency);
        IdeaModuleDependency moduleDependency = (IdeaModuleDependency)dependencies.getAt(0);

        assertEquals("dependencyProject", moduleDependency.getDependencyModule().getName());
        assertEquals("COMPILE", moduleDependency.getScope().getScope());
        assertTrue(moduleDependency.getExported());
      }
      else {
        fail();
      }
    }
  }

  @Test
  public void testGradleIdeaPluginPlusScopesDependenciesModel() throws Exception {
    ModelDependenciesBuilderImpl dependenciesBuilder = new ModelDependenciesBuilderImpl();
    assertTrue(dependenciesBuilder.canBuild("org.jetbrains.plugins.gradle.model.ProjectDependenciesModel"));

    DomainObjectSet<? extends IdeaModule> ideaModules = allModels.getIdeaProject().getModules();

    final int modulesSize = 5;
    assertEquals(modulesSize, ideaModules.size());

    for (IdeaModule ideaModule : ideaModules) {
      DomainObjectSet<? extends IdeaDependency> dependencies = ideaModule.getDependencies();
      if (ideaModule.getName().equals("lib") ||
          ideaModule.getName().equals("testGradleIdeaPluginPlusScopesDependenciesModel")) {
        assertTrue((dependencies.isEmpty()));
      }
      else if (ideaModule.getName().equals("api")) {
        assertEquals(1, dependencies.size());
        IdeaDependency libDependency = dependencies.getAt(0);
        assertEquals(GradleDependencyScope.PROVIDED.getIdeaMappingName(), libDependency.getScope().getScope().toLowerCase());
        assertTrue(libDependency instanceof IdeaModuleDependency);

        IdeaModuleDependency libModuleDependency = (IdeaModuleDependency)libDependency;
        assertNotNull(libModuleDependency.getDependencyModule());
        assertEquals("lib", libModuleDependency.getDependencyModule().getName());
      }
      else if (ideaModule.getName().equals("service")) {
        assertEquals(1, dependencies.size());
        IdeaDependency apiDependency = dependencies.getAt(0);
        assertEquals(GradleDependencyScope.COMPILE.getIdeaMappingName(), apiDependency.getScope().getScope().toLowerCase());
        assertTrue(apiDependency instanceof IdeaModuleDependency);

        IdeaModuleDependency apiModuleDependency = (IdeaModuleDependency)apiDependency;
        assertNotNull(apiModuleDependency.getDependencyModule());
        assertEquals("api", apiModuleDependency.getDependencyModule().getName());
      }
      else if (ideaModule.getName().equals("withIdeaModelCustomisations")) {

        assertTrue(findLocalLibraries(dependencies, GradleDependencyScope.TEST_COMPILE.getIdeaMappingName()).isEmpty());

        List<IdeaSingleEntryLibraryDependency> libraryDependencies =
          findLocalLibraries(dependencies, GradleDependencyScope.COMPILE.getIdeaMappingName());
        assertEquals(2, libraryDependencies.size());

        IdeaSingleEntryLibraryDependency someDep = libraryDependencies.get(0);
        assertEquals(GradleDependencyScope.COMPILE.getIdeaMappingName(), someDep.getScope().getScope().toLowerCase());
        assertEquals("someDep.jar", someDep.getFile().getName());

        IdeaSingleEntryLibraryDependency someTestDep = libraryDependencies.get(1);
        assertEquals(GradleDependencyScope.COMPILE.getIdeaMappingName(), someTestDep.getScope().getScope().toLowerCase());
        assertEquals("someTestDep.jar", someTestDep.getFile().getName());
      }
      else {
        fail();
      }
    }
  }

  @NotNull
  private static List<IdeaSingleEntryLibraryDependency> findLocalLibraries(
    @NotNull final DomainObjectSet<? extends IdeaDependency> dependencies, @NotNull final String scope) {
    return ContainerUtil.mapNotNull(
      dependencies,
      new Function<IdeaDependency, IdeaSingleEntryLibraryDependency>() {
        @Override
        public IdeaSingleEntryLibraryDependency fun(IdeaDependency dependency) {
          return dependency instanceof IdeaSingleEntryLibraryDependency && scope.equals(dependency.getScope().getScope().toLowerCase())
                 ? (IdeaSingleEntryLibraryDependency)dependency
                 : null;
        }
      }
    );
  }

  @Override
  protected Set<Class> getModels() {
    return ContainerUtil.<Class>set(ProjectDependenciesModel.class);
  }
}
