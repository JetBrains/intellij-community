/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyAccessibilityInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

class GrLatestHighlightingTest extends GrHighlightingTestBase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyLightProjectDescriptor.GROOVY_LATEST_REAL_JDK
  }

  @Override
  InspectionProfileEntry[] getCustomInspections() {
    [new GroovyAssignabilityCheckInspection(), new GrUnresolvedAccessInspection(), new GroovyAccessibilityInspection()]
  }

  void 'test IDEA-184690'() {
    testHighlighting '''\
import groovy.transform.CompileStatic

@CompileStatic
def com() {
    BigDecimal[] c = [2, 3]
    c == [2,3] as BigDecimal[] 
}
'''
  }

  void 'test IDEA-184690-2'() {
    testHighlighting '''\
import groovy.transform.CompileStatic

@CompileStatic
def com() {
    new Object() == 1
}
'''
  }

  void 'test IDEA-184690-3'() {
    testHighlighting '''\
import groovy.transform.CompileStatic

@CompileStatic
def com() {
    new Thread[1] == new Object[1]
}
'''
  }
}
