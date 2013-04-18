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

import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.project.change.ExternalProjectStructureChangesCalculator;
import com.intellij.openapi.externalSystem.service.project.change.ExternalProjectChangesCalculationContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.RootPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.diff.GradleDiffUtil;
import org.jetbrains.plugins.gradle.diff.contentroot.GradleContentRootStructureChangesCalculator;
import org.jetbrains.plugins.gradle.diff.dependency.GradleLibraryDependencyStructureChangesCalculator;
import org.jetbrains.plugins.gradle.diff.dependency.GradleModuleDependencyStructureChangesCalculator;
import com.intellij.openapi.externalSystem.service.project.ModuleAwareContentRoot;

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
public class GradleModuleStructureChangesCalculator implements ExternalProjectStructureChangesCalculator<ModuleData, Module> {
  
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
  public void calculate(@NotNull ModuleData gradleEntity,
                        @NotNull Module ideEntity,
                        @NotNull ExternalProjectChangesCalculationContext context)
  {
    // Content roots.
    // TODO den implement
//    final Collection<ContentRootData> gradleContentRoots = gradleEntity.getContentRoots();
//    final Collection<ModuleAwareContentRoot> intellijContentRoots = context.getPlatformFacade().getContentRoots(ideEntity);
//    GradleDiffUtil.calculate(myContentRootCalculator, gradleContentRoots, intellijContentRoots, context);
    
    // Dependencies.
    checkDependencies(gradleEntity, ideEntity, context); 
  }

  @NotNull
  public Object getIdeKey(@NotNull Module entity) {
    return entity.getName();
  }

  @NotNull
  public Object getGradleKey(@NotNull ModuleData entity, @NotNull ExternalProjectChangesCalculationContext context) {
    return entity.getName();
  }

  private void checkDependencies(@NotNull ModuleData gradleModule,
                                 @NotNull Module intellijModule,
                                 @NotNull ExternalProjectChangesCalculationContext context)
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
    final List<ModuleDependencyData> gradleModuleDependencies = new ArrayList<ModuleDependencyData>();
    final List<LibraryDependencyData> gradleLibraryDependencies = new ArrayList<LibraryDependencyData>();
    // TODO den implement
//    ExternalEntityVisitor visitor = new ExternalEntityVisitorAdapter() {
//      @Override
//      public void visit(@NotNull ModuleDependencyData dependency) {
//        gradleModuleDependencies.add(dependency);
//      }
//
//      @Override
//      public void visit(@NotNull LibraryDependencyData dependency) {
//        gradleLibraryDependencies.add(dependency);
//      }
//    };
//    for (DependencyData dependency : gradleModule.getDependencies()) {
//      dependency.invite(visitor);
//    }

    // Calculate changes.
    GradleDiffUtil.calculate(myLibraryDependencyCalculator, gradleLibraryDependencies, intellijLibraryDependencies, context);
    GradleDiffUtil.calculate(myModuleDependencyCalculator, gradleModuleDependencies, intellijModuleDependencies, context);
  }
}
