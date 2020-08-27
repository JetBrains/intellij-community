// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase

@CompileStatic
class GrAnnotationReferencingUnknownIdentifiersTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_2_5

  final GrAnnotationReferencingUnknownIdentifiers inspection = new GrAnnotationReferencingUnknownIdentifiers()
  final @Language("Groovy") String mapPrefix = """
import groovy.transform.MapConstructor
"""
  final @Language("Groovy") String tuplePrefix = """
import groovy.transform.TupleConstructor
"""

  private void doTest(String before) {
    fixture.with {
      enableInspections inspection
      if (before.contains("TupleConstructor")) before = tuplePrefix + before
      if (before.contains("MapConstructor")) before = mapPrefix + before
      configureByText '_.groovy', before
      checkHighlighting()
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

  void 'test raw string'() {
    doTest """
@TupleConstructor(includes = "  a, b   ,   <warning>c</warning>")
class Rr {
  boolean a
  String b
}
"""
  }

  void 'test map constructor'() {
    doTest """
@MapConstructor(includes = "  aa, bb  , <warning>cd</warning>")
class Rr {
  boolean aa
  String bb
}
"""
  }

  void 'test super class'() {
    doTest """
class Nn {
  String a
}

@TupleConstructor(includes = " a,  b,  <warning>c</warning>", includeSuperProperties = true)
class Rr extends Nn {
  int b
}
"""
  }


  void 'test ignored super class'() {
    doTest """
class Nn {
  String a
}

@TupleConstructor(includes = " <warning>a</warning>,  b,  <warning>c</warning>")
class Rr extends Nn {
  int b
}
"""
  }
}
