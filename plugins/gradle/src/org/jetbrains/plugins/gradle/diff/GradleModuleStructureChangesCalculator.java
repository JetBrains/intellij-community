/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.diff;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Encapsulates functionality of calculating changes between Gradle and IntelliJ IDEA module hierarchies.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since 11/14/11 6:30 PM
 */
public class GradleModuleStructureChangesCalculator implements GradleStructureChangesCalculator<GradleModule, Module> {
  
  private final GradleLibraryDependencyStructureChangesCalculator myLibraryDependencyCalculator;
  private final PlatformFacade myStructureHelper;

  public GradleModuleStructureChangesCalculator(@NotNull GradleLibraryDependencyStructureChangesCalculator libraryDependencyCalculator,
                                                @NotNull PlatformFacade structureHelper)
  {
    myLibraryDependencyCalculator = libraryDependencyCalculator;
    myStructureHelper = structureHelper;
  }

  @Override
  public void calculate(@NotNull GradleModule gradleEntity,
                        @NotNull Module intellijEntity,
                        @NotNull Set<GradleProjectStructureChange> knownChanges,
                        @NotNull Set<GradleProjectStructureChange> currentChanges)
  {
    //TODO den process module-local settings
    //TODO den process content roots
    checkDependencies(gradleEntity, intellijEntity, knownChanges, currentChanges); 
  }

  @NotNull
  @Override
  public Object getIntellijKey(@NotNull Module entity) {
    return entity.getName();
  }

  @NotNull
  @Override
  public Object getGradleKey(@NotNull GradleModule entity, @NotNull Set<GradleProjectStructureChange> knownChanges) {
    // TODO den consider the known changes
    return entity.getName();
  }

  private void checkDependencies(@NotNull GradleModule gradleModule,
                                 @NotNull Module intellijModule,
                                 @NotNull Set<GradleProjectStructureChange> knownChanges,
                                 @NotNull Set<GradleProjectStructureChange> currentChanges)
  {
    // Prepare intellij part.
    final List<ModuleOrderEntry> intellijModuleDependencies = new ArrayList<ModuleOrderEntry>();
    final List<LibraryOrderEntry> intellijLibraryDependencies = new ArrayList<LibraryOrderEntry>();
    RootPolicy<Object> policy = new RootPolicy<Object>() {
      @Override
      public Object visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry, Object value) {
        intellijModuleDependencies.add(moduleOrderEntry);
        return moduleOrderEntry;
      }

      @Override
      public Object visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, Object value) {
        intellijLibraryDependencies.add(libraryOrderEntry);
        return libraryOrderEntry;
      }
    };
    for (OrderEntry orderEntry : myStructureHelper.getOrderEntries(intellijModule)) {
      orderEntry.accept(policy, null);
    }

    // Prepare gradle part.
    final List<GradleModuleDependency> gradleModuleDependencies = new ArrayList<GradleModuleDependency>();
    final List<GradleLibraryDependency> gradleLibraryDependencies = new ArrayList<GradleLibraryDependency>();
    GradleEntityVisitor visitor = new GradleEntityVisitorAdapter() {
      @Override
      public void visit(@NotNull GradleModuleDependency dependency) {
        gradleModuleDependencies.add(dependency);
      }

      @Override
      public void visit(@NotNull GradleLibraryDependency dependency) {
        gradleLibraryDependencies.add(dependency);
      }
    };
    for (GradleDependency dependency : gradleModule.getDependencies()) {
      dependency.invite(visitor);
    }

    // Calculate changes.
    // TODO den process module dependencies here as well.
    GradleDiffUtil.calculate(myLibraryDependencyCalculator, gradleLibraryDependencies, intellijLibraryDependencies,
                             knownChanges, currentChanges);
  }
}
