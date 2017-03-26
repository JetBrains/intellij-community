/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import static com.intellij.openapi.externalSystem.util.ExternalSystemConstants.*;
import static org.easymock.EasyMock.*;
import static org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY;
import static org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Vladislav.Soroka
 * @since 2/28/2017
 */
public class GradleProjectResolverUtilTest {

  @Test
  public void testGetGradlePath() throws Exception {
    assertNull(GradleProjectResolverUtil.getGradlePath(null));

    final Module rootModule = createModuleMock("rootModule");
    assertEquals(":", GradleProjectResolverUtil.getGradlePath(rootModule));
    verify(rootModule);

    final Module subModule = createModuleMock(":foo:subModule");
    assertEquals(":foo:subModule", GradleProjectResolverUtil.getGradlePath(subModule));
    verify(subModule);

    final Module sourceSetModule = createModuleMock("rootModule:main", GRADLE_SOURCE_SET_MODULE_TYPE_KEY);
    assertEquals(":", GradleProjectResolverUtil.getGradlePath(sourceSetModule));
    verify(sourceSetModule);

    final Module sourceSetSubModule = createModuleMock(":foo:subModule:main", GRADLE_SOURCE_SET_MODULE_TYPE_KEY);
    assertEquals(":foo:subModule", GradleProjectResolverUtil.getGradlePath(sourceSetSubModule));
    verify(sourceSetSubModule);
  }

  @NotNull
  private static Module createModuleMock(@Nullable String projectId) {
    return createModuleMock(projectId, null);
  }

  @NotNull
  private static Module createModuleMock(@Nullable String projectId, @Nullable String moduleType) {
    final Module mockModule = createMock(Module.class);
    expect(mockModule.isDisposed()).andReturn(false).anyTimes();
    expect(mockModule.getOptionValue(EXTERNAL_SYSTEM_ID_KEY)).andReturn(SYSTEM_ID.getId()).anyTimes();
    expect(mockModule.getOptionValue(EXTERNAL_SYSTEM_MODULE_TYPE_KEY)).andReturn(moduleType).anyTimes();
    expect(mockModule.getOptionValue(LINKED_PROJECT_ID_KEY)).andReturn(projectId).anyTimes();
    replay(mockModule);
    return mockModule;
  }
}
