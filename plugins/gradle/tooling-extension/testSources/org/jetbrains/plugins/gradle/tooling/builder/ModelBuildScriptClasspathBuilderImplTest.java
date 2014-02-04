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
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.BuildScriptClasspathModel;
import org.jetbrains.plugins.gradle.model.ClasspathEntryModel;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static junit.framework.Assert.*;
import static org.junit.Assert.assertTrue;

/**
 * @author Vladislav.Soroka
 * @since 1/16/14
 */
public class ModelBuildScriptClasspathBuilderImplTest extends AbstractModelBuilderTest {

  public ModelBuildScriptClasspathBuilderImplTest(@NotNull String gradleVersion) {
    super(gradleVersion);
  }

  @Test
  public void testModelBuildScriptClasspathBuilder() throws Exception {

    DomainObjectSet<? extends IdeaModule> ideaModules = allModels.getIdeaProject().getModules();

    List<BuildScriptClasspathModel> ideaModule =
      ContainerUtil.mapNotNull(ideaModules, new Function<IdeaModule, BuildScriptClasspathModel>() {
        @Override
        public BuildScriptClasspathModel fun(IdeaModule module) {
          BuildScriptClasspathModel classpathModel = allModels.getExtraProject(module, BuildScriptClasspathModel.class);

          if (module.getName().equals("moduleWithAdditionalClasspath")) {
            assertNotNull(classpathModel);
            assertEquals(1, classpathModel.getClasspath().size());

            ClasspathEntryModel classpathEntry = classpathModel.getClasspath().getAt(0);
            assertEquals("someDep.jar", classpathEntry.getClassesFile().getName());
          }
          else if (module.getName().equals("baseModule") ||
                   module.getName().equals("moduleWithInheritedClasspath")) {
            assertNotNull("Null build classpath for module: " + module.getName(), classpathModel);
            assertEquals("Wrong build classpath for module: " + module.getName(), 1, classpathModel.getClasspath().size());

            ClasspathEntryModel classpathEntry = classpathModel.getClasspath().getAt(0);
            assertEquals("Wrong build classpath for module: " + module.getName(), "inheritedDep.jar",
                         classpathEntry.getClassesFile().getName());
          }
          else if (module.getName().equals("moduleWithoutAdditionalClasspath") ||
                   module.getName().equals("testModelBuildScriptClasspathBuilder")) {
            assertNotNull("Wrong build classpath for module: " + module.getName(), classpathModel);
            assertTrue("Wrong build classpath for module: " + module.getName(), classpathModel.getClasspath().isEmpty());
          }
          else {
            fail("Unexpected module found: " + module.getName());
          }

          return classpathModel;
        }
      });

    assertEquals(5, ideaModule.size());
  }

  @Override
  protected Set<Class> getModels() {
    return ContainerUtil.<Class>set(BuildScriptClasspathModel.class);
  }
}
