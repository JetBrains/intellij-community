// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.threadingModelHelper;

import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

final class TMHBuildProcessParametersProvider extends BuildProcessParametersProvider {
  private static final String INSTRUMENT_ANNOTATIONS_PROPERTY = "tmh.instrument.annotations";
  private static final String GENERATE_LINE_NUMBERS_PROPERTY = "tmh.generate.line.numbers";

  @Override
  public @NotNull List<String> getVMArguments() {
    List<String> args = new ArrayList<>();
    if (Registry.is("tmh.generate.assertions.for.annotations")) {
      args.add("-D" + INSTRUMENT_ANNOTATIONS_PROPERTY + "=true");
    }
    if (Registry.is("tmh.generate.line.numbers")) {
      args.add("-D" + GENERATE_LINE_NUMBERS_PROPERTY + "=true");
    }
    return args;
  }
}
