/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testIntegration.TestLocationProvider;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/** to be removed in IDEA 16 */
public class CompositeTestLocationProvider implements SMTestLocator {
  @SuppressWarnings("deprecation") private final TestLocationProvider myPrimaryLocator;
  @SuppressWarnings("deprecation") private final TestLocationProvider[] myLocators;

  @SuppressWarnings("deprecation")
  public CompositeTestLocationProvider(@Nullable TestLocationProvider primaryLocator) {
    myPrimaryLocator = primaryLocator;
    myLocators = Extensions.getExtensions(TestLocationProvider.EP_NAME);
  }

  @NotNull
  @Override
  public List<Location> getLocation(@NotNull String protocol, @NotNull String path, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    boolean isDumbMode = DumbService.isDumb(project);

    if (myPrimaryLocator != null && (!isDumbMode || myPrimaryLocator instanceof DumbAware)) {
      List<Location> locations = myPrimaryLocator.getLocation(protocol, path, project);
      if (!locations.isEmpty()) {
        return locations;
      }
    }

    if (URLUtil.FILE_PROTOCOL.equals(protocol)) {
      List<Location> locations = FileUrlProvider.INSTANCE.getLocation(protocol, path, project, scope);
      if (!locations.isEmpty()) {
        return locations;
      }
    }

    for (@SuppressWarnings("deprecation") TestLocationProvider provider : myLocators) {
      if (!isDumbMode || provider instanceof DumbAware) {
        List<Location> locations = provider.getLocation(protocol, path, project);
        if (!locations.isEmpty()) {
          return locations;
        }
      }
    }

    return Collections.emptyList();
  }
}
