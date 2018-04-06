// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class ResolvePropertyViaAliasedImportTest extends GrHighlightingTestBase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST
  final String basePath = TestUtils.testDataPath + 'resolve/imports'

  @Override
  void setUp() {
    super.setUp()
    fixture.addFileToProject 'com/foo/Bar.groovy', '''\
package com.foo
class Bar {
  static def myProperty = 'hello'
}
'''
  }

  private void doTest() {
    fixture.enableInspections GrUnresolvedAccessInspection, GroovyAssignabilityCheckInspection
    fixture.testHighlighting "${getTestName()}.groovy"
  }

  void 'test getter with alias'() {
    doTest()
  }

  void 'test getter with getter alias'() {
    doTest()
  }

  void 'test getter with setter alias'() {
    doTest()
  }

  void 'test property with alias'() {
    doTest()
  }

  void 'test property with getter alias'() {
    doTest()
  }

  void 'test property with setter alias'() {
    doTest()
  }

  void 'test setter with alias'() {
    doTest()
  }

  void 'test setter with getter alias'() {
    doTest()
  }

  void 'test setter with setter alias'() {
    doTest()
  }
}
