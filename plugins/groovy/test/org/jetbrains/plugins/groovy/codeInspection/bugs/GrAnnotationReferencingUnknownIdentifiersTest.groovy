// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs

import com.intellij.codeInspection.actions.CleanupAllIntention
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.LightGroovyTestCase

@CompileStatic
class GrAnnotationReferencingUnknownIdentifiersTest extends LightGroovyTestCase {

  final GrAnnotationReferencingUnknownIdentifiers inspection = new GrAnnotationReferencingUnknownIdentifiers()
  final String prefix = "import groovy.transform.TupleConstructor\n\n"

  private void doTest(String before, String after = null) {
    fixture.with {
      enableInspections inspection
      configureByText '_.groovy', prefix + before
      checkHighlighting()
      if (after != null) {
        launchAction CleanupAllIntention.INSTANCE
        checkResult after
      }
    }
  }

  void 'test includes'() {
    doTest """
@TupleConstructor(includes = ["a", "b", <warning>"c"</warning>])
class Rr {
  boolean a
  String b
}
"""
  }

  void 'test excludes'() {
    doTest """
@TupleConstructor(excludes = ["a", "b", <warning>"c"</warning>])
class Rr {
  boolean a
  String b
}
"""
  }
}
