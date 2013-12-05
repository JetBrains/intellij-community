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

import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.plugins.gradle.model.ProjectDependenciesModel;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

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

    IdeaModule ideaModule = ContainerUtil.find(ideaModules, new Condition<IdeaModule>() {
      @Override
      public boolean value(IdeaModule module) {
        return module.getName().equals("group2-subgroup11-project");
      }
    });

    assertNotNull(ideaModule);

    DomainObjectSet<? extends IdeaDependency> dependencies = ideaModule.getDependencies();
    assertEquals(1, dependencies.size());
  }

  @Override
  protected Set<Class> getModels() {
    return ContainerUtil.<Class>set(ProjectDependenciesModel.class);
  }
}
