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
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.plugins.gradle.model.WarModel;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Vladislav.Soroka
 * @since 11/29/13
 */
public class WarModelBuilderImplTest extends AbstractModelBuilderTest {

  @Test
  public void testDefaultWarModel() throws Exception {
    WarModelBuilderImpl warModelBuilder = new WarModelBuilderImpl();
    assertTrue(warModelBuilder.canBuild("org.jetbrains.plugins.gradle.model.WarModel"));

    DomainObjectSet<? extends IdeaModule> ideaModules = allModels.getIdeaProject().getModules();

    List<WarModel> ideaModule = ContainerUtil.mapNotNull(ideaModules, new Function<IdeaModule, WarModel>() {
      @Override
      public WarModel fun(IdeaModule module) {
        return allModels.getExtraProject(module, WarModel.class);
      }
    });

    assertEquals(1, ideaModule.size());
    WarModel warModel = ideaModule.get(0);

    assertEquals("src/main/webapp", warModel.getWebAppDirName());
  }

  @Override
  protected Set<Class> getModels() {
    return ContainerUtil.<Class>set(WarModel.class);
  }
}
