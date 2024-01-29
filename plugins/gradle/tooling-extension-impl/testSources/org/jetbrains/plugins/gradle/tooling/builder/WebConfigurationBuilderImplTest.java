// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ProjectImportAction.AllModels;
import org.jetbrains.plugins.gradle.model.web.WebConfiguration;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

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
    AllModels allModels = fetchAllModels(WebConfiguration.class);

    DomainObjectSet<? extends IdeaModule> ideaModules = allModels.getModel(IdeaProject.class).getModules();
    assertEquals(2, ideaModules.size());
    IdeaModule ideaModule = ContainerUtil.find(ideaModules, it -> it.getName().equals("testDefaultWarModel"));
    assertNotNull(ideaModule);

    WebConfiguration webConfiguration = allModels.getModel(ideaModule, WebConfiguration.class);
    assertEquals(1, webConfiguration.getWarModels().size());
    WarModel warModel = webConfiguration.getWarModels().get(0);

    String webAppDirPath = FileUtil.toSystemIndependentName(warModel.getWebAppDir().getAbsolutePath());
    assertTrue("Expect", webAppDirPath.endsWith("src/main/webapp"));

    List<String> webResources = ContainerUtil.map(warModel.getWebResources(), it -> it.getFile().getName());
    assertEquals(Arrays.asList("MANIFEST.MF", "additionalWebInf", "rootContent"), webResources);
  }
}
