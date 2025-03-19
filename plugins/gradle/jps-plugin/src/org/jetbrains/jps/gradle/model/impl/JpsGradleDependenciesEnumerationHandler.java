// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.gradle.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.gradle.model.JpsGradleExtensionService;
import org.jetbrains.jps.gradle.model.JpsGradleModuleExtension;
import org.jetbrains.jps.model.java.impl.JpsJavaDependenciesEnumerationHandler;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.Collection;

/**
 * @author Vladislav.Soroka
 */
public class JpsGradleDependenciesEnumerationHandler extends JpsJavaDependenciesEnumerationHandler {
  private static final JpsGradleDependenciesEnumerationHandler SOURCE_SET_TYPE_INSTANCE = new JpsGradleDependenciesEnumerationHandler(true);
  private static final JpsGradleDependenciesEnumerationHandler NON_SOURCE_SET_TYPE_INSTANCE =
    new JpsGradleDependenciesEnumerationHandler(false);

  private final boolean myResolveModulePerSourceSet;

  public JpsGradleDependenciesEnumerationHandler(boolean resolveModulePerSourceSet) {
    myResolveModulePerSourceSet = resolveModulePerSourceSet;
  }

  @Override
  public boolean shouldAddRuntimeDependenciesToTestCompilationClasspath() {
    return !myResolveModulePerSourceSet;
  }

  @Override
  public boolean isProductionOnTestsDependency(JpsDependencyElement element) {
    return JpsGradleExtensionService.getInstance().isProductionOnTestDependency(element);
  }

  @Override
  public boolean shouldIncludeTestsFromDependentModulesToTestClasspath() {
    return !myResolveModulePerSourceSet;
  }

  @Override
  public boolean shouldProcessDependenciesRecursively() {
    return !myResolveModulePerSourceSet;
  }

  public static class GradleFactory extends Factory {
    @Override
    public @Nullable JpsJavaDependenciesEnumerationHandler createHandler(@NotNull Collection<JpsModule> modules) {
      JpsGradleExtensionService service = JpsGradleExtensionService.getInstance();
      JpsJavaDependenciesEnumerationHandler handler = null;
      for (JpsModule module : modules) {
        JpsGradleModuleExtension gradleModuleExtension = service.getExtension(module);
        if (gradleModuleExtension != null) {
          if (JpsGradleModuleExtension.GRADLE_SOURCE_SET_MODULE_TYPE_KEY.equals(gradleModuleExtension.getModuleType())) {
            handler = SOURCE_SET_TYPE_INSTANCE;
            break;
          }
          else {
            handler = NON_SOURCE_SET_TYPE_INSTANCE;
          }
        }
      }
      return handler;
    }
  }
}
