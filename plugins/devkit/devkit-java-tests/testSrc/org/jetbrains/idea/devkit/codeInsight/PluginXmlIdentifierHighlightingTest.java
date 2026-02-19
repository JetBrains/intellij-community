// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.codeInsight;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.List;

public class PluginXmlIdentifierHighlightingTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.setReadEditorMarkupModel(true);
  }

  public void test_ep_references() {
    IdentifierHighlighterPassFactory.doWithIdentifierHighlightingEnabled(getProject(), () -> {

      myFixture.configureByText("plugin.xml", """
<idea-plugin>
  <extensionPoints>
    <extensionPoint name="foo<caret>.bar"/>
  </extensionPoints>
  <extensions>
    <foo.bar/>
  </extensions>
</idea-plugin>
""");
      List<HighlightInfo> infos = myFixture.doHighlighting();
      assertEquals(2, infos.stream().filter(it ->
        it.getSeverity() == HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY
      ).count());
    });
  }
}
