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
package org.jetbrains.plugins.gradle.tooling.builder;

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaModuleDependency;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * @author Vladislav.Soroka
 * @since 11/29/13
 */
public class ModelDependenciesBuilderImplTest extends AbstractModelBuilderTest {

  public ModelDependenciesBuilderImplTest(@NotNull String gradleVersion) {
    super(gradleVersion);
  }

  @Test
  public void testDefaultDependenciesModel() throws Exception {
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

        if(GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("2.5")) < 0) {
          assertTrue(moduleDependency.getExported());
        } else {
          assertFalse(moduleDependency.getExported());
        }

      }
      else {
        fail();
      }
    }
  }

  @Test
  public void testGradleIdeaPluginPlusScopesDependenciesModel() throws Exception {
    DomainObjectSet<? extends IdeaModule> ideaModules = allModels.getIdeaProject().getModules();

    final int modulesSize = 6;
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
        assertEquals("provided", libDependency.getScope().getScope().toLowerCase(Locale.ENGLISH));
        assertTrue(libDependency instanceof IdeaModuleDependency);

        IdeaModuleDependency libModuleDependency = (IdeaModuleDependency)libDependency;
        assertNotNull(libModuleDependency.getDependencyModule());
        assertEquals("lib", libModuleDependency.getDependencyModule().getName());
      }
      else if (ideaModule.getName().equals("service")) {
        assertEquals(1, dependencies.size());
        IdeaDependency apiDependency = dependencies.getAt(0);
        assertEquals("compile", apiDependency.getScope().getScope().toLowerCase(Locale.ENGLISH));
        assertTrue(apiDependency instanceof IdeaModuleDependency);

        IdeaModuleDependency apiModuleDependency = (IdeaModuleDependency)apiDependency;
        assertNotNull(apiModuleDependency.getDependencyModule());
        assertEquals("api", apiModuleDependency.getDependencyModule().getName());
      }
      else if (ideaModule.getName().equals("withIdeaModelCustomisations")) {

        assertTrue(findLocalLibraries(dependencies, "test").isEmpty());

        List<IdeaSingleEntryLibraryDependency> libraryDependencies =
          findLocalLibraries(dependencies, "compile");
        assertEquals(2, libraryDependencies.size());

        IdeaSingleEntryLibraryDependency someDep = libraryDependencies.get(0);
        assertEquals("compile", someDep.getScope().getScope().toLowerCase(Locale.ENGLISH));
        assertEquals("someDep.jar", someDep.getFile().getName());

        IdeaSingleEntryLibraryDependency someTestDep = libraryDependencies.get(1);
        assertEquals("compile", someTestDep.getScope().getScope().toLowerCase(Locale.ENGLISH));
        assertEquals("someTestDep.jar", someTestDep.getFile().getName());
      }
      else if (ideaModule.getName().equals("withIdeRepoFileDependency")) {
        assertEquals(2, dependencies.size());
        assertTrue(dependencies.getAt(0) instanceof IdeaSingleEntryLibraryDependency);
        IdeaSingleEntryLibraryDependency libraryDependency = (IdeaSingleEntryLibraryDependency)dependencies.getAt(0);

        assertNotNull(libraryDependency.getGradleModuleVersion());
        assertEquals("junit", libraryDependency.getGradleModuleVersion().getName());
        assertEquals("junit", libraryDependency.getGradleModuleVersion().getGroup());
        assertEquals("4.11", libraryDependency.getGradleModuleVersion().getVersion());
        assertEquals("TEST", libraryDependency.getScope().getScope());
        assertFalse(libraryDependency.getExported());
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
          return dependency instanceof IdeaSingleEntryLibraryDependency &&
                 scope.equals(dependency.getScope().getScope().toLowerCase(Locale.ENGLISH))
                 ? (IdeaSingleEntryLibraryDependency)dependency : null;
        }
      }
    );
  }

  @Override
  protected Set<Class> getModels() {
    return Collections.emptySet();
  }
}
