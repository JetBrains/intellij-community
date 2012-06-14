/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.execution.testframework.sm;

import com.intellij.execution.Location;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.testIntegration.TestLocationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Sergey Simonchik
 */
public class CompositeTestLocationProvider implements TestLocationProvider {

  private final TestLocationProvider myPrimaryLocator;

  public CompositeTestLocationProvider(@Nullable TestLocationProvider primaryLocator) {
    myPrimaryLocator = primaryLocator;
  }

  @NotNull
  @Override
  public List<Location> getLocation(@NotNull String protocolId, @NotNull String locationData, Project project) {
    if (myPrimaryLocator != null) {
      List<Location> locations = myPrimaryLocator.getLocation(protocolId, locationData, project);
      if (!locations.isEmpty()) {
        return locations;
      }
    }
    final boolean isDumbMode = DumbService.isDumb(project);
    for (TestLocationProvider provider : Extensions.getExtensions(TestLocationProvider.EP_NAME)) {
      if (isDumbMode && !DumbService.isDumbAware(provider)) {
        continue;
      }
      final List<Location> locations = provider.getLocation(protocolId, locationData, project);
      if (!locations.isEmpty()) {
        return locations;
      }
    }
    return Collections.emptyList();
  }
}
