// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.gradle.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.ModuleBasedBuildTargetType;
import org.jetbrains.jps.gradle.model.JpsGradleExtensionService;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public final class GradleResourcesTargetType extends ModuleBasedBuildTargetType<GradleResourcesTarget> {
  public static final GradleResourcesTargetType PRODUCTION = new GradleResourcesTargetType("gradle-resources-production", false);
  public static final GradleResourcesTargetType TEST = new GradleResourcesTargetType("gradle-resources-test", true);

  private final boolean myIsTests;

  private GradleResourcesTargetType(final String typeId, boolean isTests) {
    super(typeId, true);
    myIsTests = isTests;
  }

  public boolean isTests() {
    return myIsTests;
  }

  @Override
  public @NotNull List<GradleResourcesTarget> computeAllTargets(@NotNull JpsModel model) {
    final List<GradleResourcesTarget> targets = new ArrayList<>();
    for (JpsModule module : model.getProject().getModules()) {
      if (JpsGradleExtensionService.getInstance().getExtension(module) != null) {
        targets.add(new GradleResourcesTarget(this, module));
      }
    }
    return targets;
  }

  @Override
  public @NotNull BuildTargetLoader<GradleResourcesTarget> createLoader(@NotNull JpsModel model) {
    return new BuildTargetLoader<GradleResourcesTarget>() {
      @Override
      public @Nullable GradleResourcesTarget createTarget(@NotNull String targetId) {
        final JpsModule module = model.getProject().findModuleByName(targetId);
        return module != null ? new GradleResourcesTarget(GradleResourcesTargetType.this, module) : null;
      }
    };
  }

  public static List<GradleResourcesTarget> buildModuleTargets(@NotNull JpsModule module, boolean includeTest) {
    List<GradleResourcesTarget> targets = new ArrayList<>();
    targets.add(new GradleResourcesTarget(PRODUCTION, module));
    if (includeTest) {
      targets.add(new GradleResourcesTarget(TEST, module));
    }
    return targets;
  }
}
