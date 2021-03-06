// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.codeInsight

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import groovy.transform.CompileStatic

@CompileStatic
class PluginXmlIdentifierHighlightingTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp()
    myFixture.readEditorMarkupModel = true
  }

  void 'test ep references'() {
    IdentifierHighlighterPassFactory.doWithHighlightingEnabled(getProject(), getTestRootDisposable()) {
      myFixture.configureByText 'plugin.xml', '''\
<idea-plugin>
  <extensionPoints>
    <extensionPoint name="foo<caret>.bar"/>
  </extensionPoints>
  <extensions>
    <foo.bar/>
  </extensions>
</idea-plugin>
'''
      def infos = myFixture.doHighlighting()
      assert infos.findAll {
        it.severity == HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY
      }.size() == 2
    }
  }
}
