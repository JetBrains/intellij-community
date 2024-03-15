// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

import static com.intellij.codeInsight.daemon.impl.HighlightInfoType.ELEMENT_UNDER_CARET_READ
import static com.intellij.codeInsight.daemon.impl.HighlightInfoType.ELEMENT_UNDER_CARET_WRITE
import static com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory.doWithHighlightingEnabled

@CompileStatic
class GroovyHighlightUsagesTest extends LightGroovyTestCase {

  private static final SeveritiesProvider SEVERITIES_PROVIDER = new SeveritiesProvider() {
    final List<HighlightInfoType> severitiesHighlightInfoTypes = [ELEMENT_UNDER_CARET_READ, ELEMENT_UNDER_CARET_WRITE]
  }

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  final String basePath = TestUtils.testDataPath + 'highlighting/usages/'

  @Override
  void setUp() throws Exception {
    super.setUp()
    myFixture.setReadEditorMarkupModel(true)
  }

  private void doTest(boolean directoryTest = false) {
    SeveritiesProvider.EP_NAME.getPoint().registerExtension(SEVERITIES_PROVIDER, testRootDisposable)
    doWithHighlightingEnabled(getProject(), getTestRootDisposable()) {
      def name = getTestName()
      if (directoryTest) {
        fixture.copyDirectoryToProject(name, "")
        fixture.configureByFile("$name/test.groovy")
      }
      else {
        fixture.configureByFile("${name}.groovy")
      }
      fixture.checkHighlighting()
    }
  }

  void 'test constructor usages 1'() { doTest() }

  void 'test constructor usages 2'() { doTest() }

  void 'test constructor usages 3'() { doTest() }

  void 'test constructor usages 4'() { doTest(true) }

  void 'test class usages 1'() { doTest() }

  void 'test class usages 2'() { doTest() }

  void 'test binding variable'() { doTest() }
}
