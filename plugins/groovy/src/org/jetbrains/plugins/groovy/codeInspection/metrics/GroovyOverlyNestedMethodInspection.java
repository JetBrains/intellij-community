// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.metrics;

import com.intellij.codeInspection.options.OptPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;

import static com.intellij.codeInspection.options.OptPane.number;
import static com.intellij.codeInspection.options.OptPane.pane;

public class GroovyOverlyNestedMethodInspection extends GroovyOverlyNestedMethodInspectionBase {

  @Override
  public @NotNull OptPane getGroovyOptionsPane() {
    return pane(
      number("m_limit", GroovyBundle.message("overly.nested.method.nesting.limit.option"), 1, 100));
  }
}
