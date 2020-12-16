// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.style

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.style.string.GrStringStyleViolationInspection
import org.jetbrains.plugins.groovy.codeInspection.style.string.GrStringStyleViolationInspection.InspectionStringQuotationKind

import static org.jetbrains.plugins.groovy.codeInspection.style.string.GrStringStyleViolationInspection.InspectionStringQuotationKind.*

@CompileStatic
class GrStringStyleViolationInspectionTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST
  final GrStringStyleViolationInspection inspection = new GrStringStyleViolationInspection()

  void 'test plain string correction'() {
    doTest '"abc"', "'abc'", plain: SINGLE_QUOTED
  }

  void 'test no complaint on correct kind'() {
    doTest "'abc'", plain: SINGLE_QUOTED
  }

  void 'test correction to slashy string'() {
    doTest "'''abc'''", "/abc/", plain: SLASHY
  }

  void 'test multiline'() {
    doTest """'''abc
cde'''""", """/abc
cde/""", multiline: SLASHY
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
    doTest '"abc${1}de"', '/abc${1}de/', interpolation: SLASHY
  }

  void "test don't complain to interpolated string if settings are disabled"() {
    doTest '"${1}"', interpolation: UNDEFINED
  }

  void "test don't complain to interpolated string if its kind coincides with settings"() {
    doTest '"""${1}"""', interpolation: TRIPLE_DOUBLE_QUOTED
  }

  void "test escaping minimization"() {
    doTest '"ab\\"c"', /'ab"c'/, plain: DOUBLE_QUOTED, escape: SINGLE_QUOTED
  }

  void "test escaping minimization 2"() {
    doTest(/"ab\"'c"/, '''$/ab"'c/$''', plain: DOUBLE_QUOTED, escape: SINGLE_QUOTED)
  }

  void "test consider slashes for slashy strings"() {
    doTest($/"ab//\"c"/$, '''$/ab//"c/$''', plain: DOUBLE_QUOTED, escape: SLASHY)
  }

  void "test complex minimization"() {
    doTest("""'\$ /\$ \$\$\$ //\\n'""", plain: DOUBLE_QUOTED, escape: SLASHY)
  }

  void "test conversion to dollar-slashy string"() {
    doTest '\'abc$de\'', '$/abc$$de/$', plain: DOLLAR_SLASHY_QUOTED, escape: UNDEFINED
  }

  private void doTest(Map<String, InspectionStringQuotationKind> map = [:], String before, String after = null) {
    inspection.with {
      plainStringQuotation$intellij_groovy_psi = map.plain ?: SINGLE_QUOTED
      escapedStringQuotation$intellij_groovy_psi = map.escape ?: UNDEFINED
      interpolatedStringQuotation$intellij_groovy_psi = map.interpolation ?: UNDEFINED
      multilineStringQuotation$intellij_groovy_psi = map.multiline ?: TRIPLE_QUOTED
    }
    fixture.with {
      enableInspections inspection
      configureByText '_.groovy', before
      if (after == null) {
        checkHighlighting(true, false, true)
      } else {
        def intention = availableIntentions.find { it.familyName.startsWith("Convert to") || it.familyName.contains("Change quotes") }
        launchAction intention
        checkResult after
      }
      null
    }
  }
}
