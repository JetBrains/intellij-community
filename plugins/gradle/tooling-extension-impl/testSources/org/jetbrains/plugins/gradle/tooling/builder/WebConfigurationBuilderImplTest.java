// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder;

import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.web.WebConfiguration;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static org.jetbrains.plugins.gradle.model.web.WebConfiguration.WarModel;
import static org.junit.Assert.*;

/**
 * @author Vladislav.Soroka
 */
public class WebConfigurationBuilderImplTest extends AbstractModelBuilderTest {

  public WebConfigurationBuilderImplTest(@NotNull String gradleVersion) {
    super(gradleVersion);
  }

  @Test
  public void testDefaultWarModel() {
    DomainObjectSet<? extends IdeaModule> ideaModules = allModels.getModel(IdeaProject.class).getModules();

    List<WebConfiguration> ideaModule = ContainerUtil.mapNotNull(ideaModules, module -> allModels.getModel(module, WebConfiguration.class));

    assertEquals(1, ideaModule.size());
    WebConfiguration webConfiguration = ideaModule.get(0);
    assertEquals(1, webConfiguration.getWarModels().size());

    final WarModel warModel = webConfiguration.getWarModels().iterator().next();
    assertTrue("Expect", toSystemIndependentName(warModel.getWebAppDir().getAbsolutePath())
      .endsWith("src/main/webapp"));

    assertArrayEquals(
      new String[]{"MANIFEST.MF", "additionalWebInf", "rootContent"},
      ContainerUtil.map2Array(warModel.getWebResources(), resource -> resource.getFile().getName()));
  }

  @Override
  protected Set<Class<?>> getModels() {
    return Collections.singleton(WebConfiguration.class);
  }
}
