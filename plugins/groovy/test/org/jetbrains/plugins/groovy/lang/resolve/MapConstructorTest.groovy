// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.codeInspection.LocalInspectionTool
import groovy.transform.CompileStatic
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyConstructorNamedArgumentsInspection
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.HighlightingTest
import org.junit.Test

@CompileStatic
class MapConstructorTest extends GroovyLatestTest implements HighlightingTest {

  private void doTest(String text, Class<? extends LocalInspectionTool>... inspections) {
    @Language("Groovy") String newText = text
    if (text.contains("MapConstructor")) {
      newText = "import groovy.transform.MapConstructor\n" + newText
    }
    if (text.contains("CompileStatic")) {
      newText = "import groovy.transform.CompileStatic\n" + newText
    }
    highlightingTest(newText, inspections)
  }

  @Test
  void 'explicit map constructor'() {
    doTest """
@MapConstructor
class Rr {
    String actionType = ""
    long referrerCode
    boolean referrerUrl
    
    Rr(String s) { int x = 1; }
}

@CompileStatic
static void main(String[] args) {
    new Rr(actionType: "a", referrerCode: 10, referrerUrl: true)
}
"""
  }

  @Test
  void 'no explicit map constructor'() {
    doTest """
class Rr {
    String actionType = ""
    long referrerCode
    boolean referrerUrl
    
    Rr(String s) { int x = 1; }
}

@CompileStatic
static void main(String[] args) {
    new Rr<error>(actionType: "a", referrerCode: 10, referrerUrl: true)</error>
}
"""
  }

  @Test
  void 'pre and post resolving'() {
    doTest """
class NN {}

@CompileStatic
@MapConstructor(pre = { super(); }, post = { assert referrerUrl = true })
class Rr extends NN {
    String actionType = ""
    long referrerCode
    boolean referrerUrl
    
    Rr(String s) { int x = 1; }
}

@CompileStatic
static void main(String[] args) {
    new Rr(actionType: "a", referrerCode: 10, referrerUrl: true)
}
"""
  }

  @Test
  void 'unknown label'() {
    doTest """
@MapConstructor(excludes = "actionType")
class Rr {
    String actionType
    long referrerCode;
    boolean referrerUrl;
}

@CompileStatic
static void main(String[] args) {
    def x = new Rr(<warning>actionType</warning>: "abc")
}""", GroovyConstructorNamedArgumentsInspection
  }

  @Test
  void 'static property'() {
    doTest """
@MapConstructor(excludes = "actionType")
class Rr {
    String actionType
    long referrerCode;
    static boolean referrerUrl
}

@CompileStatic
static void main(String[] args) {
    def x = new Rr(referrerCode: 10, <warning>referrerUrl</warning>: true)
}""", GroovyConstructorNamedArgumentsInspection
  }
}
