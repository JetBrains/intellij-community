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
package org.jetbrains.plugins.gradle.diff.module;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.RootPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.diff.GradleChangesCalculationContext;
import org.jetbrains.plugins.gradle.diff.GradleDiffUtil;
import org.jetbrains.plugins.gradle.diff.GradleStructureChangesCalculator;
import org.jetbrains.plugins.gradle.diff.contentroot.GradleContentRootStructureChangesCalculator;
import org.jetbrains.plugins.gradle.diff.dependency.GradleLibraryDependencyStructureChangesCalculator;
import org.jetbrains.plugins.gradle.diff.dependency.GradleModuleDependencyStructureChangesCalculator;
import org.jetbrains.plugins.gradle.model.gradle.*;
import org.jetbrains.plugins.gradle.model.intellij.ModuleAwareContentRoot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Encapsulates functionality of calculating changes between Gradle and IntelliJ IDEA module hierarchies.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since 11/14/11 6:30 PM
 */
public class GradleModuleStructureChangesCalculator implements GradleStructureChangesCalculator<GradleModule, Module> {
  
  @NotNull private final GradleLibraryDependencyStructureChangesCalculator myLibraryDependencyCalculator;
  @NotNull private final GradleModuleDependencyStructureChangesCalculator myModuleDependencyCalculator;
  @NotNull private final GradleContentRootStructureChangesCalculator myContentRootCalculator;

  public GradleModuleStructureChangesCalculator(@NotNull GradleLibraryDependencyStructureChangesCalculator libraryDependencyCalculator,
                                                @NotNull GradleModuleDependencyStructureChangesCalculator moduleDependencyCalculator,
                                                @NotNull GradleContentRootStructureChangesCalculator calculator)
  {
    myLibraryDependencyCalculator = libraryDependencyCalculator;
    myModuleDependencyCalculator = moduleDependencyCalculator;
    myContentRootCalculator = calculator;
  }

  @Override
  public void calculate(@NotNull GradleModule gradleEntity,
                        @NotNull Module intellijEntity,
                        @NotNull GradleChangesCalculationContext context)
  {
    // Content roots.
    final Collection<GradleContentRoot> gradleContentRoots = gradleEntity.getContentRoots();
    final Collection<ModuleAwareContentRoot> intellijContentRoots = context.getPlatformFacade().getContentRoots(intellijEntity);
    GradleDiffUtil.calculate(myContentRootCalculator, gradleContentRoots, intellijContentRoots, context);
    
    // Dependencies.
    checkDependencies(gradleEntity, intellijEntity, context); 
  }

  @NotNull
  @Override
  public Object getIntellijKey(@NotNull Module entity) {
    return entity.getName();
  }

  @NotNull
  @Override
  public Object getGradleKey(@NotNull GradleModule entity, @NotNull GradleChangesCalculationContext context) {
    // TODO den consider the known changes
    return entity.getName();
  }

  private void checkDependencies(@NotNull GradleModule gradleModule,
                                 @NotNull Module intellijModule,
                                 @NotNull GradleChangesCalculationContext context)
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
    for (OrderEntry orderEntry : context.getPlatformFacade().getOrderEntries(intellijModule)) {
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
    GradleDiffUtil.calculate(myLibraryDependencyCalculator, gradleLibraryDependencies, intellijLibraryDependencies, context);
    GradleDiffUtil.calculate(myModuleDependencyCalculator, gradleModuleDependencies, intellijModuleDependencies, context);
  }
}
