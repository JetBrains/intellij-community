// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.spock

import com.intellij.codeInspection.LocalInspectionTool
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.util.HighlightingTest
import org.junit.Test

@CompileStatic
class SpockHighlightingTest extends SpockTestBase implements HighlightingTest {

  @Override
  Collection<? extends Class<? extends LocalInspectionTool>> getInspections() {
    return [GroovyAssignabilityCheckInspection]
  }

  @Test
  void 'single pipe column separator'() {
    highlightingTest '''\
class FooSpec extends spock.lang.Specification {
  def feature(int a, boolean b) {
    where:
    a | b
    1 | true
    and:
    2 | false
  }
  
  def feature(int a) {
    where:
    a | _
    1 | _
    and:
    2 | _
  }
}
'''
  }
}
