// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.confusing.UnnecessaryQualifiedReferenceInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;

public class GrRuntimeMixinTest extends GrHighlightingTestBase {

  public void testIsNotUnnecessarilyQualified() {
    doTestHighlighting("""
                         class SomeCategoryClass {
                             static Integer addAmount(Integer self, int amount) { self + amount }
                         }
                         Integer.mixin(SomeCategoryClass)
                         assert 1.addAmount(1) == 2
                         """, GrUnresolvedAccessInspection.class, GroovyAssignabilityCheckInspection.class,
                       UnnecessaryQualifiedReferenceInspection.class);
  }
}
