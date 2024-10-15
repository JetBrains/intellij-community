// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections;

import org.jetbrains.plugins.groovy.codeInspection.confusing.GroovyImplicitNullArgumentCallInspection;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;

public class GroovyImplicitNullArgumentCallInspectionTest extends GrHighlightingTestBase {
  private void doTest(String text) {
    myFixture.configureByText("_.groovy", text);

    myFixture.enableInspections(GroovyImplicitNullArgumentCallInspection.class);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testShowWeakWarning() {
    doTest("""
             def foo(x) {}
             foo<weak_warning>()</weak_warning>
             """);
  }

  public void testNoWarningIfNullWasPassedExplicitly() {
    doTest("""
             def foo(x) {}
             foo(null)
             """);
  }
}
