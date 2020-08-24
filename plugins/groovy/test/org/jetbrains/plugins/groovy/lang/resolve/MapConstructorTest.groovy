// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve


import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.HighlightingTest
import org.junit.Test

@CompileStatic
class MapConstructorTest extends GroovyLatestTest implements HighlightingTest {

  @Test
  void 'explicit map constructor'() {
    highlightingTest """
@groovy.transform.MapConstructor
class Rr {
    String actionType = ""
    long referrerCode
    boolean referrerUrl
    
    Rr(String s) { int x = 1; }
}

@groovy.transform.CompileStatic
static void main(String[] args) {
    new Rr(actionType: "a", referrerCode: 10, referrerUrl: true)
}
"""
  }

  @Test
  void 'no explicit map constructor'() {

    highlightingTest """
class Rr {
    String actionType = ""
    long referrerCode
    boolean referrerUrl
    
    Rr(String s) { int x = 1; }
}

@groovy.transform.CompileStatic
static void main(String[] args) {
    new Rr<error>(actionType: "a", referrerCode: 10, referrerUrl: true)</error>
}
"""
    getFixture().checkHighlighting(true, true, true)
  }
}
