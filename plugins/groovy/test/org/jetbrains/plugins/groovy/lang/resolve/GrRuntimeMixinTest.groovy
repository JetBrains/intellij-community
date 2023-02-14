// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.confusing.UnnecessaryQualifiedReferenceInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase

@CompileStatic
class GrRuntimeMixinTest extends GrHighlightingTestBase {

  void 'test is not unnecessarily qualified'() {
    doTestHighlighting '''\
class SomeCategoryClass {
    static Integer addAmount(Integer self, int amount) { self + amount }
}
Integer.mixin(SomeCategoryClass)
assert 1.addAmount(1) == 2
''', GrUnresolvedAccessInspection, GroovyAssignabilityCheckInspection, UnnecessaryQualifiedReferenceInspection
  }
}
