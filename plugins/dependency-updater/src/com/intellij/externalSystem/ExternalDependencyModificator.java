package com.intellij.externalSystem;

import com.intellij.buildsystem.model.DeclaredDependency;
import com.intellij.buildsystem.model.unified.UnifiedDependency;
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Experimental
public interface ExternalDependencyModificator {
  ExtensionPointName<ExternalDependencyModificator> EP_NAME = ExtensionPointName.create("com.intellij.externalSystem.dependencyModifier");

  boolean supports(@NotNull Module module);

  void addDependency(@NotNull Module module, @NotNull UnifiedDependency descriptor);

  void updateDependency(@NotNull Module module,
                        @NotNull UnifiedDependency oldDescriptor,
                        @NotNull UnifiedDependency newDescriptor);

  void removeDependency(@NotNull Module module, @NotNull UnifiedDependency descriptor);

  void addRepository(@NotNull Module module, @NotNull UnifiedDependencyRepository repository);

  void deleteRepository(@NotNull Module module, @NotNull UnifiedDependencyRepository repository);

  List<DeclaredDependency> declaredDependencies(@NotNull Module module);

  List<UnifiedDependencyRepository> declaredRepositories(@NotNull Module module);
}
