// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util;

import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public interface HighlightingTest extends BaseTest {

  @Nullable
  default String getTestName() {
    return null;
  }

  @NotNull
  default Collection<Class<? extends LocalInspectionTool>> getInspections() {
    return Collections.emptyList();
  }

  default void highlightingTest() {
    getFixture().enableInspections(getInspections());
    getFixture().testHighlighting(getTestName() + ".groovy");
  }

  default void highlightingTest(String text) {
    getFixture().enableInspections(getInspections());
    configureByText(text);
    getFixture().checkHighlighting();
  }

  default void highlightingTest(String text, LocalInspectionTool... inspections) {
    getFixture().enableInspections(inspections);
    highlightingTest(text);
  }

  default void highlightingTest(String text, Class<? extends LocalInspectionTool>... inspections) {
    getFixture().enableInspections(inspections);
    highlightingTest(text);
  }
}
