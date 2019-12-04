// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.LocalInspectionTool
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyAccessibilityInspection
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.HighlightingTest
import org.junit.Test

@CompileStatic
class GrAccessRightsTest extends GroovyLatestTest implements HighlightingTest {

  GrAccessRightsTest() {
    super('highlighting')
  }

  @Override
  Collection<Class<? extends LocalInspectionTool>> getInspections() {
    Arrays.asList(GroovyAccessibilityInspection.class)
  }

  @Test
  void 'constructor'() {
    fileHighlightingTest('accessConstructor.groovy')
  }

  @Test
  void 'property'() {
    fileHighlightingTest('accessProperty.groovy')
  }

  @Test
  void 'trait method'() {
    fileHighlightingTest('accessTraitMethod.groovy')
  }

  @Test
  void 'method @CS'() {
    fileHighlightingTest('accessMethodCS.groovy')
  }

  @Test
  void 'static imported property'() {
    fileHighlightingTest('accessStaticImportedProperty.groovy')
  }
}
