// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.style

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.style.GrStringStyleViolationInspection.InspectionStringKind

import static org.jetbrains.plugins.groovy.codeInspection.style.GrStringStyleViolationInspection.InspectionStringKind.*

@CompileStatic
class GrStringStyleViolationInspectionTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST
  final GrStringStyleViolationInspection inspection = new GrStringStyleViolationInspection()

  void 'test plain string correction'() {
    doTest '''
<weak_warning>"abc"</weak_warning>
''', plain: SINGLE_QUOTED
  }

  void 'test no complaint on correct kind'() {
    doTest '''
'abc'
''', plain: SINGLE_QUOTED
  }

  void 'test correction to slashy string'() {
    doTest """
<weak_warning>'''abc'''</weak_warning>
""", plain: SLASHY
  }

  void 'test multiline'() {
    doTest """
<weak_warning>'''abc
cde'''</weak_warning>
""", multiline: SLASHY
  }


  void "test don't complain to multiline string if settings are disabled"() {
    doTest """
'''abc
cde'''
""", multiline: UNDEFINED
  }

  void "test don't complain to multiline string if its kind coincides with settings"() {
    doTest '''
"""abc
cde"""
''', multiline: TRIPLE_DOUBLE_QUOTED
  }

  void "test interpolated string"() {
    doTest '''
<weak_warning>"${1}"</weak_warning>
''', interpolation: SLASHY
  }

  void "test don't complain to interpolated string if settings are disabled"() {
    doTest '''
"${1}"
''', interpolation: UNDEFINED
  }

  void "test don't complain to interpolated string if its kind coincides with settings"() {
    doTest '''
"""${1}"""
''', interpolation: TRIPLE_DOUBLE_QUOTED
  }

  void "test escaping minimization"() {
    doTest """
<weak_warning>"ab\\"c"</weak_warning>
""", plain: DOUBLE_QUOTED, escape: SINGLE_QUOTED
  }


  void "test leave plain string if escaping can't be minimized"() {
    doTest """
<weak_warning>"ab\\"'c"</weak_warning>
""", plain: DOUBLE_QUOTED, escape: SINGLE_QUOTED
  }

  void "test consider slashes for slashy strings"() {
    doTest """
<weak_warning>"ab//\\"c"</weak_warning>
""", plain: DOUBLE_QUOTED, escape: SLASHY
  }

  private void doTest(Map<String, InspectionStringKind> map = [:], String before) {
    inspection.with {
      plainVersion$intellij_groovy_psi = map.plain ?: SINGLE_QUOTED
      escapeVersion$intellij_groovy_psi = map.escape ?: UNDEFINED
      interpolationVersion$intellij_groovy_psi = map.interpolation ?: UNDEFINED
      multilineVersion$intellij_groovy_psi = map.multiline ?: TRIPLE_QUOTED
    }
    fixture.with {
      enableInspections inspection
      configureByText '_.groovy', before
      checkHighlighting(true, false, true)
    }
  }
}
