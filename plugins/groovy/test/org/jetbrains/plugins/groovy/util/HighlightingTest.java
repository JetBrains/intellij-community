// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util;

import com.intellij.codeInspection.LocalInspectionTool;
import org.intellij.lang.annotations.Language;
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

  default void highlightingTest(@Language("Groovy") String text) {
    getFixture().enableInspections(getInspections());
    configureByText(text);
    getFixture().checkHighlighting();
  }
}
