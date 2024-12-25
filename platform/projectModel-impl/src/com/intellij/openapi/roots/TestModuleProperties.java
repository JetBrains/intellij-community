// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * In some cases tests need to be extracted to a separate module (because they have a different classpath, output folder or JDK). E.g. when
 * the project is imported from Gradle IDEA creates separate modules for each source set of a Gradle project.
 * <p/>
 * This service allows to specify to which production module the tests module belongs. This information may be used for example by
 * 'Create Test' feature.
 * <p/>
 * <strong>This API isn't stable for now and may be changed in future. Also it isn't possible to change this in UI.</strong>
 */
public abstract class TestModuleProperties {
  public static TestModuleProperties getInstance(@NotNull Module module) {
    return module.getService(TestModuleProperties.class);
  }

  @ApiStatus.Internal
  protected TestModuleProperties() {
  }

  public abstract @Nullable String getProductionModuleName();

  public abstract @Nullable Module getProductionModule();

  public abstract void setProductionModuleName(@Nullable String moduleName);
}
