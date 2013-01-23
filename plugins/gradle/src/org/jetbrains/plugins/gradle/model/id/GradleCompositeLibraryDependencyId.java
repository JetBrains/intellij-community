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
package org.jetbrains.plugins.gradle.model.id;

import com.intellij.openapi.roots.LibraryOrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.GradleEntityType;
import org.jetbrains.plugins.gradle.model.gradle.GradleCompositeLibraryDependency;
import org.jetbrains.plugins.gradle.model.gradle.GradleLibraryDependency;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureHelper;
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext;

/**
 * @author Denis Zhdanov
 * @since 1/22/13 5:26 PM
 */
public class GradleCompositeLibraryDependencyId extends AbstractGradleCompositeEntityId<GradleLibraryDependencyId> {

  public GradleCompositeLibraryDependencyId(@NotNull GradleLibraryDependencyId gradleLibraryDependencyId,
                                            @NotNull GradleLibraryDependencyId ideLibraryDependencyId)
  {
    super(GradleEntityType.DEPENDENCY_TO_OUTDATED_LIBRARY, GradleEntityOwner.IDE, ideLibraryDependencyId, gradleLibraryDependencyId);
  }

  @Nullable
  @Override
  public GradleCompositeLibraryDependency mapToEntity(@NotNull GradleProjectStructureContext context) {
    GradleProjectStructureHelper helper = context.getProjectStructureHelper();
    GradleLibraryDependency gradleLibraryDependency = helper.findGradleLibraryDependency(getCounterPartyId());
    if (gradleLibraryDependency == null) {
      return null;
    }

    LibraryOrderEntry ideLibraryDependency = helper.findIdeLibraryDependency(getBaseId());
    if (ideLibraryDependency == null) {
      return null;
    }

    return new GradleCompositeLibraryDependency(gradleLibraryDependency, ideLibraryDependency);
  }
}
