// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes

import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class CreateConstantFromJavaUsageTest extends GrHighlightingTestBase {
  final String getBasePath() {
    return TestUtils.testDataPath + 'fixes/createConstantFromJava/' + getTestName(true) + '/'
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyLightProjectDescriptor.GROOVY_LATEST_REAL_JDK
  }

  private void doTest(String action = 'Create constant', int actionsCount = 1) {
    final String beforeGroovy = "Before.groovy"
    final String afterGroovy = "After.groovy"
    final String javaClass = "Area.java"

    fixture.with {
      configureByFiles(javaClass, beforeGroovy)

      enableInspections(customInspections)
      def fixes = filterAvailableIntentions(action)
      assert fixes.size() == actionsCount
      if (actionsCount == 0) return
      launchAction fixes.first()
      PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
      checkResultByFile(beforeGroovy, afterGroovy, true)
    }
  }

  void testInterface() {
    doTest()
  }

  void testConstantInitializer() {
    doTest()
  }

  void testUppercaseInSuperInterface() {
    doTest( "Create constant field 'BAR' in 'I'", 0)
  }
}