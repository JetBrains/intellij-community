// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyConstructorNamedArgumentsInspection

class MapConstructorAttributesFixTest extends GrIntentionTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_2_5_REAL_JDK

  MapConstructorAttributesFixTest() {
    super("Add necessary", GroovyConstructorNamedArgumentsInspection)
  }

  void 'test apply'() {
    doTextTest """
@groovy.transform.MapConstructor
class Rr {
    private String actionType
}

static void main(String[] args) {
    def x = new Rr(actio<caret>nType: "")
}
""", """
@groovy.transform.MapConstructor(includeFields = true)
class Rr {
    private String actionType
}

static void main(String[] args) {
    def x = new Rr(actio<caret>nType: "")
}
"""
  }
}
