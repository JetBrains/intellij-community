// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ExternalProjectSystemRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import static org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY;
import static org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Vladislav.Soroka
 */
public class GradleProjectResolverUtilTest {

  @Test
  public void testGetGradlePath() {
    final Module rootModule = createModuleMock("rootModule");
    assertEquals(":", GradleProjectResolverUtil.getGradlePath(rootModule));

    final Module subModule = createModuleMock(":foo:subModule");
    assertEquals(":foo:subModule", GradleProjectResolverUtil.getGradlePath(subModule));

    final Module compositeBuildSubModule = createModuleMock("composite:subModule");
    assertEquals(":subModule", GradleProjectResolverUtil.getGradlePath(compositeBuildSubModule));

    final Module sourceSetModule = createModuleMock("rootModule:main", GRADLE_SOURCE_SET_MODULE_TYPE_KEY);
    assertEquals(":", GradleProjectResolverUtil.getGradlePath(sourceSetModule));

    final Module sourceSetSubModule = createModuleMock(":foo:subModule:main", GRADLE_SOURCE_SET_MODULE_TYPE_KEY);
    assertEquals(":foo:subModule", GradleProjectResolverUtil.getGradlePath(sourceSetSubModule));
  }

  @NotNull
  private static Module createModuleMock(@Nullable String projectId) {
    return createModuleMock(projectId, null);
  }

  @NotNull
  private static Module createModuleMock(@Nullable String projectId, @Nullable String moduleType) {
    Module module = mock(Module.class);
    Project project = mock(Project.class);
    when(module.getProject()).thenReturn(project);
    ExternalSystemModulePropertyManager modulePropertyManager = new ExternalSystemModulePropertyManagerImpl(module);
    when(module.getService(ExternalSystemModulePropertyManager.class)).thenReturn(modulePropertyManager);

    when(module.getOptionValue(ExternalProjectSystemRegistry.EXTERNAL_SYSTEM_ID_KEY)).thenReturn(SYSTEM_ID.getId());
    when(module.getOptionValue("external.system.module.type")).thenReturn(moduleType);
    when(module.getOptionValue("external.linked.project.id")).thenReturn(projectId);
    return module;
  }
}
