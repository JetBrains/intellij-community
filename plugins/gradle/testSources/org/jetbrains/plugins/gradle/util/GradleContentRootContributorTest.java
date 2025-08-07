// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemContentRootContributor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class GradleContentRootContributorTest extends GradleImportingTestCase {
  @Test
  public void testContentRoots() throws Throwable {
    createProjectSubFile("settings.gradle", "include('submodule')");
    createProjectSubFile("submodule/build.gradle", "apply plugin:'java'");

    importProject(
      "apply plugin: 'java'\n" +
      "sourceSets { main { java { srcDirs 'java2/mySrc/' } } }");

    List<ExternalSystemSourceType> systemSourceTypes = ContainerUtil.map(ExternalSystemSourceType.values(), (v) -> v);

    check(
      "project",
      systemSourceTypes,
      Pair.create(".gradle", ExternalSystemSourceType.EXCLUDED),
      Pair.create("build", ExternalSystemSourceType.EXCLUDED),
      Pair.create("src/test/java", ExternalSystemSourceType.TEST),
      Pair.create("src/test/resources", ExternalSystemSourceType.TEST_RESOURCE),
      Pair.create("src/main/java", ExternalSystemSourceType.SOURCE),
      Pair.create("src/main/resources", ExternalSystemSourceType.RESOURCE),
      Pair.create("java2/mySrc", ExternalSystemSourceType.SOURCE)
    );

    check(
      "project",
      List.of(ExternalSystemSourceType.SOURCE),
      Pair.create("src/main/java", ExternalSystemSourceType.SOURCE),
      Pair.create("java2/mySrc", ExternalSystemSourceType.SOURCE)
    );

    check(
      "project.submodule",
      systemSourceTypes,
      Pair.create("submodule/.gradle", ExternalSystemSourceType.EXCLUDED),
      Pair.create("submodule/build", ExternalSystemSourceType.EXCLUDED),
      Pair.create("submodule/src/test/java", ExternalSystemSourceType.TEST),
      Pair.create("submodule/src/test/resources", ExternalSystemSourceType.TEST_RESOURCE),
      Pair.create("submodule/src/main/java", ExternalSystemSourceType.SOURCE),
      Pair.create("submodule/src/main/resources", ExternalSystemSourceType.RESOURCE)
    );
  }

  private void check(@NotNull String moduleName,
                     Collection<ExternalSystemSourceType> sourceTypes,
                     Pair<String, ExternalSystemSourceType>... expected) {
    Module module = ReadAction.compute(() -> ModuleManager.getInstance(getMyProject()).findModuleByName(moduleName));
    assertNotNull(moduleName, module);
    Collection<ExternalSystemContentRootContributor.ExternalContentRoot> contentRoots =
      ExternalSystemApiUtil.getExternalProjectContentRoots(module, sourceTypes);

    String projectRootPath = myProjectRoot.getPath();
    List<Pair<String, ExternalSystemSourceType>> map = ContainerUtil.map(
      contentRoots,
      it -> Pair.create(FileUtil.getRelativePath(projectRootPath, FileUtil.toSystemIndependentName(it.getPath().toString()), '/'),
                        it.getRootType()));

    assertSameElements(map, expected);
  }


  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @Parameterized.Parameters(name = "with Gradle-{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{{BASE_GRADLE_VERSION}});
  }
}