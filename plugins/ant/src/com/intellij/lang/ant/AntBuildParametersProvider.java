// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant;

import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.lang.ant.config.impl.GlobalAntConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

final class AntBuildParametersProvider extends BuildProcessParametersProvider {
  @Override
  public @NotNull List<String> getVMArguments() {
    try {
      File bundledAntHome = GlobalAntConfiguration.getBundledAntHome();
      return List.of("-Djps.bundled.ant.path=" + bundledAntHome.getAbsolutePath());
    }
    catch (IllegalStateException e) {
      // seems broken installation, try to work at least for non-bundled Ant
      Logger.getInstance(AntBuildParametersProvider.class).warn("Unable to find bundled Ant for JPS build", e);

      return List.of();
    }
  }
}
