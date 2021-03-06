// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.threadingModelHelper;

import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class TMHBuildProcessParametersProvider extends BuildProcessParametersProvider {
  private static final String INSTRUMENT_ANNOTATIONS_PROPERTY = "tmh.instrument.annotations";

  @Override
  public @NotNull List<String> getVMArguments() {
    if (Registry.is("tmh.generate.assertions.for.annotations")) {
      return Collections.singletonList("-D" + INSTRUMENT_ANNOTATIONS_PROPERTY + "=true");
    }
    return super.getVMArguments();
  }
}
